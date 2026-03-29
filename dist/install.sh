#!/bin/bash
# JavaPlanner installer
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PKG="$SCRIPT_DIR/JavaPlanner-1.0.pkg"

echo "Installing JavaPlanner..."
if [ ! -f "$PKG" ]; then
  echo "Error: JavaPlanner-1.0.pkg not found next to this script."
  exit 1
fi

sudo installer -pkg "$PKG" -target /
if [ $? -eq 0 ]; then
  echo "JavaPlanner installed successfully. Launch it from /Applications."
else
  echo "Installation failed."
  exit 1
fi
