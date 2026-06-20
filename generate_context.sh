#!/bin/bash

# Путь к проекту и выходному файлу
SRC_DIR="/sdcard/RankedEngine/RankedEngine.ai"
OUTPUT_FILE="/sdcard/RankedEngine.txt"

# Очищаем старый файл, если он был
> "$OUTPUT_FILE"

echo "Начинаю сборку кода проекта..."

# Заходим в папку проекта
cd "$SRC_DIR" || { echo "Ошибка: папка не найдена!"; exit 1; }

# Находим только нужные текстовые файлы, исключая кэш, гитигнор и бинарники
find . -type f \( -name "*.kt" -o -name "*.xml" -o -name "*.gradle" -o -name "*.properties" -o -name "*.txt" \) \
! -path "*/.git/*" \
! -path "*/.gradle/*" \
! -path "*/build/*" \
! -name "gradlew" \
! -name "gradlew.bat" | while read -r file; do

    # Убираем точку в начале пути для красоты
    clean_path=$(echo "$file" | sed 's|^\./||')
    
    # Записываем заголовок с путем к файлу
    echo -e "\n# $clean_path" >> "$OUTPUT_FILE"
    
    # Записываем содержимое файла
    cat "$file" >> "$OUTPUT_FILE"
    
    echo "Добавлен: $clean_path"
done

echo "Готово! Все исходники объединены в файл: $OUTPUT_FILE"
