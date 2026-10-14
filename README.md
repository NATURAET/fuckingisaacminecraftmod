# Isaac in Minecraft (Fabric 1.20.1)

Сборка: JDK 17 + gradle wrapper (скопируй gradlew, gradlew.bat, gradle/ из шаблона https://fabricmc.net/develop/template/ для 1.20.1),
затем `./gradlew build` -> build/libs/isaac-in-minecraft-1.0.0.jar. В mods положи jar + Fabric API 0.92.2+1.20.1.

Игра:
  /isaac start   — становишься Айзеком: 3 сердца, выдаются Слёзы Айзека и бомба
  /isaac stop    — обычный режим
  /isaac passive — (оп) случайная пассивка
  ПКМ со Слезами = выстрел. Пассивки работают, пока лежат в инвентаре.
  Монстры дропают: монетки, сердца, бомбы, синие сердца, пассивки. Боссы (Иссушитель, Дракон, Страж, Древний страж) — гарантированная пассивка.
  Монетка (ПКМ) — игровой автомат. D6 (ПКМ) — перебрасывает все пассивки.

Свои ресурсы: замени текстуры, положив PNG в src/main/resources/assets/isaacmod/textures/item/<id>.png
и поправив "layer0" в models/item/<id>.json на "isaacmod:item/<id>".
Свои звуки: sounds.json + .ogg в assets/isaacmod/sounds/.

## Предметы (v1.1)
- 34 пассивки: статы (слёзы, урон, выстрелы, скорость, здоровье, дальность, удача) и эффекты слёз:
  самонаведение, пробивание, огонь, яд, замедление, вампиризм, взрыв, иссушение.
- 10 активок с перезарядкой: D6, Yum Heart, Necronomicon, Kamikaze!, Teleport!, Anarchist Cookbook,
  How to Jump, Book of Shadows, Hourglass, Mega Blast, Mom's Bottle of Pills.
- Расходники: монетка, бомба, красное/синее сердце, таблетка, карта Таро.
- Удача повышает шанс дропа и джекпота в монетке.

## Сборка без установки чего-либо
Залей папку на GitHub (новый репозиторий) -> вкладка Actions -> build -> Artifacts: скачай jar
(нужен именно файл без "-sources" в названии). Либо локально: JDK 17 + Gradle 8.5, команда `gradle build`.
