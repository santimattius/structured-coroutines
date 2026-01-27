#!/bin/bash

# Script para publicar Android Lint Rules
# Uso: ./publish-lint-rules.sh

set -e

echo "🔨 Compilando módulo lint-rules..."
./gradlew :lint-rules:clean :lint-rules:build

echo "📦 Creando JAR con reglas de Lint..."
./gradlew :lint-rules:lintJar

echo "📤 Publicando en Maven Local..."
./gradlew :lint-rules:publishToMavenLocal

echo "✅ Verificando publicación..."

# Verificar JAR
if [ -f "lint-rules/build/libs/structured-coroutines-lint-rules.jar" ]; then
    echo "✅ JAR creado: lint-rules/build/libs/structured-coroutines-lint-rules.jar"
    
    # Verificar manifest
    if unzip -p lint-rules/build/libs/structured-coroutines-lint-rules.jar META-INF/MANIFEST.MF | grep -q "Lint-Registry-v2"; then
        echo "✅ Manifest correcto con Lint-Registry-v2"
    else
        echo "❌ Error: Manifest no contiene Lint-Registry-v2"
    fi
    
    # Verificar servicio
    if unzip -p lint-rules/build/libs/structured-coroutines-lint-rules.jar META-INF/services/com.android.tools.lint.client.api.IssueRegistry | grep -q "StructuredCoroutinesIssueRegistry"; then
        echo "✅ Servicio registrado correctamente"
    else
        echo "❌ Error: Servicio no registrado"
    fi
else
    echo "❌ Error: JAR no encontrado"
    exit 1
fi

# Verificar publicación en Maven Local
if [ -d "$HOME/.m2/repository/io/github/santimattius/structured-coroutines-lint-rules/0.1.0" ]; then
    echo "✅ Publicado en Maven Local: ~/.m2/repository/io/github/santimattius/structured-coroutines-lint-rules/0.1.0/"
    ls -lh "$HOME/.m2/repository/io/github/santimattius/structured-coroutines-lint-rules/0.1.0/"
else
    echo "❌ Error: No se encontró en Maven Local"
    exit 1
fi

echo ""
echo "🎉 ¡Publicación exitosa!"
echo ""
echo "Para usar en tu proyecto Android, agrega:"
echo "  dependencies {"
echo "      lintChecks(\"io.github.santimattius:structured-coroutines-lint-rules:0.1.0\")"
echo "  }"
