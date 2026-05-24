#!/usr/bin/env bash
# apply-fixes-lite.sh
# Versão simplificada: pula a etapa [1/5] e aplica só as demais correções.

set -e

FIXES_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="${1:-.}"
SRC="$PROJECT_DIR/app/src/main/java/com/androidstudiomobile"

copy() {
  local src="$FIXES_DIR/app/src/main/java/com/androidstudiomobile/$1"
  local dst="$SRC/$1"
  mkdir -p "$(dirname "$dst")"
  cp "$src" "$dst"
  echo "  ✓ $1"
}

echo ""
echo "=== Applying remaining Kotlin fixes ==="
echo "Project: $PROJECT_DIR"
echo ""

echo "[2/5] Fixing data models..."
copy "data/model/Models.kt"

echo "[3/5] Fixing viewmodels..."
copy "ui/viewmodel/LogcatViewModel.kt"
copy "ui/viewmodel/ResourceManagerViewModel.kt"

echo "[4/5] Fixing core modules..."
copy "lint/LintEngine.kt"
copy "lsp/KotlinAnalyzer.kt"
copy "editor/MonacoEditorBridge.kt"
copy "navgraph/NavGraphParser.kt"
copy "navgraph/NavGraphEditorScreen.kt"
copy "profiler/AdvancedProfiler.kt"
copy "preview/PreviewPane.kt"

echo "[5/5] Fixing UI screens and navigation..."
copy "ui/navigation/AppNavigation.kt"
copy "ui/screens/ProjectsScreen.kt"
copy "ui/screens/DatabaseInspectorScreen.kt"
copy "ui/screens/DragDropLayoutEditor.kt"
copy "ui/screens/ThemeEditorScreen.kt"

echo ""
echo "=== All remaining fixes applied! ==="
echo ""
echo "Next steps:"
echo "  1. git diff para revisar mudanças"
echo "  2. ./gradlew assembleDebug"
echo ""
