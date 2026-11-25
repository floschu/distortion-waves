package at.florianschuster.distortionwaves.internal

// Ported from the Metal shaders of DistortionWavesKit (MIT, © 2026 Jakub Antalik).
// See the credits in the README.
//
// One field, three shaders. Everything the wave does is read from the same
// signed distance to its front:
//
//   d > 0  the wave has not got here yet
//   d = 0  the front
//   d < 0  the wave has passed
//
// The same source runs as AGSL on Android and as SkSL through Skiko elsewhere.
//
// All lengths are in "points" (dp times the style's scale). `unit` is the number
// of pixels in a point, so the shaders divide by it on the way in and multiply
// by it when they sample.

private const val FIELD =
    """
uniform float2 origin;
uniform float spine;
uniform float radius;
uniform float squash;
uniform float time;
uniform float energy;
uniform float swell;
uniform float2 size;
uniform float gravity;
uniform float glow;
uniform float clearRadius;
uniform float rim;
uniform float tilt;
uniform float ring;
uniform float hold;
uniform float launched;
uniform float hairClearRadius;
uniform float unit;

const float COVER = 60.0;
const float PI = 3.14159265;

// How much of the wave acts at this point: 0 on a held source, 1 well clear of it.
float heldAt(float2 p) {
    if (hold <= 0.0) return 1.0;
    return smoothstep(hold * 0.9, hold * 1.8, length(p - origin));
}

struct Field {
    float d;
    float2 n;   // points away from the source, on screen
    float2 q;   // position relative to the origin, in the wave's frame
    float2 raw; // position relative to the origin, on screen
    float g;    // how fast the raw distance grows, which d has been divided by
};

Field fieldAt(float2 p) {
    float2 raw = p - origin;
    float cs = cos(tilt);
    float sn = sin(tilt);
    float2 q = float2(cs * raw.x + sn * raw.y, -sn * raw.x + cs * raw.y);

    // A narrower x metric bows the front, so it does not cross the screen as
    // a nearly flat line.
    float mx = q.x * squash;
    float2 v = float2(max(abs(mx) - spine, 0.0), q.y);
    float l = max(length(v), 1e-3);
    float2 grad = float2(v.x * sign(q.x) * squash, v.y) / l;
    float g = max(length(grad), 1e-3);

    // A slow bend along the front, so it never looks drawn with a compass.
    float bend;
    if (ring > 0.5) {
        float a = atan(q.y, q.x);
        bend = 0.62 * sin(2.0 * a + time * 1.1) + 0.38 * sin(3.0 * a - time * 1.5 + 1.7);
    } else {
        float s = mx * 0.0058;
        bend = 0.62 * sin(s + time * 1.1) + 0.38 * sin(s * 2.1 - time * 1.5 + 1.7);
    }

    float2 n = grad / g;
    Field w;
    w.d = (l - radius) / g + swell * bend;
    w.n = float2(cs * n.x - sn * n.y, sn * n.x + cs * n.y);
    w.q = q;
    w.raw = raw;
    w.g = g;
    return w;
}
"""

// Swell. Content rises once as the front passes and settles back. Gravity is the
// anticipation: everything falls toward the source and is let go at the launch.
// The colours part along the way a pixel has been moved, so the chromatic
// aberration shows only where the content is distorted.
//
// side: +1 the content the wave leaves behind, -1 the content it reveals.
internal const val LENS_SHADER =
    FIELD +
        """
uniform shader content;
uniform float amplitude;
uniform float lensWidth;
uniform float aberration;
uniform float side;

half4 tap(float2 s, float2 split, float along, half3 weight, float2 bounds) {
    half4 c = content.eval(clamp(s + split * along, float2(0.5), bounds) * unit);
    return half4(c.rgb * weight, c.a / 5.0);
}

half4 main(float2 xy) {
    float2 p = xy / unit;
    Field w = fieldAt(p);
    float2 bounds = size - 0.5;

    // Ahead of the crossfade zone the outgoing side covers the incoming one
    // completely, and behind it the outgoing side has nothing left to draw.
    float cover = 1.0;
    if (side > 0.0) {
        if (launched > 0.5) cover = smoothstep(-COVER, COVER, w.d);
        if (cover <= 0.0) return half4(0.0);
    } else if (launched < 0.5 || w.d > COVER + 6.0) {
        // Nothing of the incoming side shows before the front has left, nor
        // ahead of the crossfade zone after.
        return half4(0.0);
    }

    // Steeper ahead, where it compresses what it is about to reach; long behind.
    float u = w.d / (lensWidth * (w.d > 0.0 ? 0.85 : 1.5));
    float lift = exp(-u * u);
    float r = max(length(w.raw), 1.0);

    float held = heldAt(p);
    float2 swollen = w.n * energy * amplitude * lift * held;
    float2 inward = w.raw / r;
    float2 s = p - swollen + inward * gravity * exp(-r / 900.0) * held;
    float2 split = (swollen - inward * gravity * exp(-r / 240.0) * held) * aberration;

    // Under a quarter of a point the colours can't be seen apart.
    if (dot(split, split) < 0.0625) {
        return content.eval(clamp(s, float2(0.5), bounds) * unit) * half(cover);
    }

    // Five samples across the spectrum: a smooth split, not three ghost images.
    half4 acc = tap(s, split, -1.0, half3(0.571, 0.000, 0.000), bounds);
    acc += tap(s, split, -0.5, half3(0.343, 0.206, 0.000), bounds);
    acc += tap(s, split, 0.0, half3(0.086, 0.588, 0.086), bounds);
    acc += tap(s, split, 0.5, half3(0.000, 0.206, 0.343), bounds);
    acc += tap(s, split, 1.0, half3(0.000, 0.000, 0.571), bounds);
    return acc * half(cover);
}
"""

// Progressive blur. Turns the blur radius this pixel should have into the
// coverage of one blurred copy of the content. Copies stacked over the sharp
// original, each taking over where the last one ends, approximate a blur whose
// radius changes across the screen.
internal const val BLUR_MASK_SHADER =
    FIELD +
        """
uniform shader content;
uniform float blurRadius;
uniform float blurAhead;
uniform float blurBehind;
uniform float blurFrom;
uniform float blurTo;

half4 main(float2 xy) {
    float2 p = xy / unit;
    Field w = fieldAt(p);
    float u = w.d / (w.d > 0.0 ? blurAhead : blurBehind);
    float rad = energy * blurRadius * exp(-u * u) * heldAt(p);
    float t = clamp((rad - blurFrom) / max(blurTo - blurFrom, 1e-3), 0.0, 1.0);
    return content.eval(xy) * half(t * t * (3.0 - 2.0 * t));
}
"""

// Colour, in two parts. The band rides the front: the palette flowing along it,
// a warm fringe ahead, a cool one behind. The rim is what the band leaves on the
// edges, until a second, later front wipes it away.
internal const val BAND_SHADER =
    FIELD +
        """
uniform float3 palette[8];  // OKLab
uniform float paletteCount;
uniform float3 warm;
uniform float3 cool;
uniform float lead;
uniform float tail;
uniform float strength;
uniform float rimWidth;
uniform float rimSoftness;
uniform float cornerRadius;
uniform float unevenness;
uniform float pulse;
uniform float hairWidth;
uniform float hairOpacity;
uniform float hairWhite;
uniform float3 lumps[7];    // centre, length, depth

float3 oklabToSRGB(float3 c) {
    float l = c.x + 0.3963377774 * c.y + 0.2158037573 * c.z;
    float m = c.x - 0.1055613458 * c.y - 0.0638541728 * c.z;
    float s = c.x - 0.0894841775 * c.y - 1.2914855480 * c.z;
    l = l * l * l; m = m * m * m; s = s * s * s;
    float3 lin = clamp(float3(
        4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    ), 0.0, 1.0);
    return mix(12.92 * lin, 1.055 * pow(lin, float3(1.0 / 2.4)) - 0.055, step(float3(0.0031308), lin));
}

float sq(float x) { return x * x; }

// Signed distance to a rect with continuous (superellipse) corners.
float continuousRectSDF(float2 p, float2 halfSize, float cr) {
    float a = min(cr * 1.528665, min(halfSize.x, halfSize.y));
    float2 q = abs(p) - (halfSize - a);
    if (q.x <= 0.0 || q.y <= 0.0) {
        return max(abs(p.x) - halfSize.x, abs(p.y) - halfSize.y);
    }
    const float n = 3.26;
    float2 u = q / a;
    float norm = pow(pow(u.x, n) + pow(u.y, n), 1.0 / n);
    float2 g = pow(u / max(norm, 1e-4), float2(n - 1.0));
    return (norm - 1.0) * a / max(length(g), 1e-3);
}

// Where a point is along the rounded-rect edge: clockwise from the top centre.
// .y is the whole perimeter.
float2 perimeterAt(float2 p, float2 halfSize, float r) {
    float ex = max(halfSize.x - r, 0.0);
    float ey = max(halfSize.y - r, 0.0);
    float arc = 0.5 * PI * r;
    float P = 4.0 * (ex + ey + arc);
    float ax = abs(p.x);
    float ay = abs(p.y);
    if (ax > ex && ay > ey) {
        float2 v = p - float2(p.x >= 0.0 ? ex : -ex, p.y >= 0.0 ? ey : -ey);
        float a;
        float base;
        if (p.x >= 0.0 && p.y < 0.0) { a = atan(v.x, -v.y);  base = ex; }
        else if (p.x >= 0.0)         { a = atan(v.y, v.x);   base = ex + arc + 2.0 * ey; }
        else if (p.y >= 0.0)         { a = atan(-v.x, v.y);  base = 3.0 * ex + 2.0 * arc + 2.0 * ey; }
        else                         { a = atan(-v.y, -v.x); base = 3.0 * ex + 3.0 * arc + 4.0 * ey; }
        return float2(base + r * clamp(a, 0.0, 0.5 * PI), P);
    }
    bool onSide = ax > ex ? true : (ay > ey ? false : (halfSize.x - ax) < (halfSize.y - ay));
    if (onSide) {
        return float2(p.x >= 0.0 ? ex + arc + (p.y + ey) : 3.0 * ex + 3.0 * arc + 2.0 * ey + (ey - p.y), P);
    }
    return float2(p.y < 0.0 ? (p.x >= 0.0 ? p.x : P + p.x) : ex + 2.0 * arc + 2.0 * ey + (ex - p.x), P);
}

// How thick the colour on the edges is at `s` along the perimeter, as a share
// of rimWidth: lumps with thin stretches between them. Averages 1.
float rimThickness(float s, float P) {
    float t = 0.08;
    for (int i = 0; i < 7; i++) {
        float ds = s - lumps[i].x;
        ds -= P * floor(ds / P + 0.5);
        t += lumps[i].z * exp(-sq(ds / lumps[i].y));
    }
    return t / 1.0498;
}

// Interleaved gradient noise: enough to hide banding, not enough to see.
float dither(float2 p) {
    return fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715)))) - 0.5;
}

// One colour from the ring of palette colours, mixed in OKLab so neighbours
// never pass through grey.
float3 paletteAt(float u) {
    float x = fract(u) * paletteCount;
    float i0 = min(floor(x), paletteCount - 1.0);
    float j0 = mod(i0 + 1.0, paletteCount);
    float m = fract(x);
    m = m * m * (3.0 - 2.0 * m);
    float3 a = palette[0];
    float3 b = palette[0];
    for (int k = 0; k < 8; k++) {
        float fk = float(k);
        if (fk == i0) a = palette[k];
        if (fk == j0) b = palette[k];
    }
    return mix(a, b, m);
}

half4 main(float2 xy) {
    float2 p = xy / unit;
    float2 c = p - size * 0.5;
    float inset = max(-continuousRectSDF(c, size * 0.5, cornerRadius), 0.0);
    bool nearEdge = rim > 0.0 && inset < max(rimWidth * 2.2 * (1.0 + 1.9 * unevenness), hairWidth * 0.5 + 0.75);
    // Once the front's colour has gone, only the edges are left to draw.
    if (glow <= 0.0 && !nearEdge) return half4(0.0);

    Field w = fieldAt(p);
    float d = w.d;

    // The tail swells and thins along the front, and is lighter in the middle.
    bool isRing = ring > 0.5;
    float turn = atan(w.q.y, w.q.x);
    float along = w.q.x * 0.0068;
    float lobes = isRing
        ? 0.5 + 0.5 * sin(2.0 * turn + time * 1.4) * cos(turn - time * 0.7 + 1.2)
        : 0.5 + 0.5 * sin(along * 1.3 + time * 1.4) * cos(along * 0.6 - time * 0.7 + 1.2);
    float bandA = 0.0;
    if (glow > 0.0 && d < lead) {
        if (d >= 0.0) {
            float k = 1.0 - d / lead;
            bandA = k * k * k * (k * (k * 6.0 - 15.0) + 10.0);
        } else {
            bandA = pow(1.0 - clamp(-d / (tail * mix(0.6, 1.3, lobes)), 0.0, 1.0), 2.3);
        }
        bandA *= glow * strength * mix(0.6, 1.0, exp(-inset / 140.0)) * heldAt(p);
    }

    float around = atan(c.y / size.y, c.x / size.x);
    float rimA = 0.0;
    float hairA = 0.0;
    float shimmer = 0.5;
    if (nearEdge) {
        float2 edge = perimeterAt(c, size * 0.5, cornerRadius);
        float thickness = 1.0;
        if (unevenness > 0.0) {
            thickness = max(1.0 + (rimThickness(edge.x, edge.y) - 1.0) * unevenness, 0.04);
        }
        float hug = exp(-sq(inset / (rimWidth * thickness)));
        float reached = 1.0 - smoothstep(-lead * 0.7, lead * 0.5, d);
        float kept = smoothstep(-rimSoftness, rimSoftness, d + (radius - clearRadius) / w.g);
        // Two soft swells of brightness running round the edge.
        shimmer = 0.5 + 0.5 * sin(time * 3.4 - edge.x / edge.y * 4.0 * PI);
        rimA = hug * reached * kept * rim * mix(1.0, 0.7 + 0.3 * shimmer, pulse);

        // A hairline right on the edge, wiped by a later, slower front.
        if (hairWidth > 0.0) {
            float line = 1.0 - smoothstep(hairWidth * 0.5, hairWidth * 0.5 + 0.75, inset);
            float hairKept = smoothstep(-rimSoftness, rimSoftness, d + (radius - hairClearRadius) / w.g);
            hairA = line * reached * hairKept * rim * hairOpacity
                * mix(0.5, 1.0, smoothstep(0.1, 1.5, thickness))
                * mix(1.0, 0.55 + 0.45 * shimmer, pulse);
        }
    }

    float a = 1.0 - (1.0 - bandA) * (1.0 - rimA);
    float total = 1.0 - (1.0 - a) * (1.0 - hairA);
    if (total <= 0.0005) return half4(0.0);

    float flow = 0.62 + (isRing ? turn / (2.0 * PI) : along * 0.2) + time * 0.13 + d * 0.0015
        + (isRing ? 0.0 : 0.05 * sin(w.q.y * 0.009 + time));
    float3 lab = paletteAt(flow);
    lab = mix(lab, warm, 0.5 * exp(-sq((d - 30.0) / 50.0)));
    lab = mix(lab, cool, 0.5 * exp(-sq((d + 84.0) / 72.0)));
    lab.yz *= 1.22;
    lab = mix(lab, float3(0.99, 0.0, 0.0), 0.7 * exp(-sq((d + 8.0) / 26.0)));

    float3 rimLab = paletteAt(around / (2.0 * PI) + time * (0.12 + 0.25 * pulse) + inset * 0.0012);
    rimLab.yz *= 1.1;
    lab = mix(lab, rimLab, rimA / (rimA + bandA + 1e-4));

    float3 rgb = oklabToSRGB(lab);
    if (hairA > 0.0) {
        float3 hair = mix(oklabToSRGB(rimLab), float3(1.0), hairWhite * (0.5 + 0.5 * shimmer));
        rgb = (rgb * a * (1.0 - hairA) + hair * hairA) / total;
    }

    float noise = dither(xy) * 2.0 / 255.0;
    return half4(half3(clamp(rgb * total + noise, 0.0, 1.0)), half(clamp(total + noise, 0.0, 1.0)));
}
"""

/** Writes the field every shader reads. `unit` is pixels per point. */
internal fun WaveShader.setField(
    front: WaveFront,
    unit: Float,
) {
    set("origin", front.originX, front.originY)
    set("spine", front.spine)
    set("radius", front.radius)
    set("squash", front.squash)
    set("time", front.time)
    set("energy", front.energy)
    set("swell", front.swell)
    set("size", front.width, front.height)
    set("gravity", front.gravity)
    set("glow", front.glow)
    set("clearRadius", front.clearRadius)
    set("rim", front.rim)
    set("tilt", front.tilt)
    set("ring", if (front.ring) 1f else 0f)
    set("hold", front.hold)
    set("launched", if (front.launched) 1f else 0f)
    set("hairClearRadius", front.hairClearRadius)
    set("unit", unit)
}
