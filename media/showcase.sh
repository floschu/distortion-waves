#!/bin/sh
# Encodes the showcase frames into media/showcase.mp4 (1080p on black, for
# sharing) and media/showcase.webp (the phones only, transparent, for the README).
#
#   (cd library && ./gradlew :sample:shared:desktopTest -Pshowcase)
#   media/showcase.sh
set -e
cd "$(dirname "$0")/.."
frames=library/sample/shared/build/showcase/f%04d.png

ffmpeg -v error -y -framerate 30 -i "$frames" \
  -filter_complex "color=black:size=3840x2160:rate=30[bg];[bg][0]overlay=shortest=1,scale=1920:1080:flags=lanczos,format=yuv420p" \
  -c:v libx264 -profile:v high -preset slow -crf 18 -movflags +faststart \
  media/showcase.mp4

# The frames are 3840 x 2160 with a clear background. The phones fill
# x = 464 to 3375 and y = 509 to 1952. The crop leaves 48 px around them.
# ffmpeg writes the cropped frames, and img2webp from libwebp joins them.
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
ffmpeg -v error -y -framerate 30 -i "$frames" \
  -vf "crop=3008:1540:416:461,fps=20,scale=1200:-1:flags=lanczos" \
  "$tmp/w%04d.png"
img2webp -loop 0 -lossy -q 70 -m 6 -d 50 "$tmp"/w*.png -o media/showcase.webp

ls -lh media/showcase.mp4 media/showcase.webp
