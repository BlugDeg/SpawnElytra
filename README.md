# SpawnElytra

Paper-Plugin (26.2) für eine fliegende Spawn-Insel.

## Die Insel
Ein Quader (inkl. Luftraum), den du selbst setzt. Er ist Spawn, Schutzzone und Absprung zugleich. **Ober- und unterhalb** des Quaders gilt nichts davon – dort darf jeder bauen.

Auf der Insel gilt: kein Bauen/Abbauen, kein PvP, keine Gegenstände (Raketen, Windkugeln, Perlen, Bögen, Dreizack …), keine Truhen/Türen/Knöpfe, kein Schaden, kein Hunger, keine feindlichen Mobs, keine Explosionen, kein Feuer, keine Flüssigkeiten/Kolben von aussen. Essen ist erlaubt. Wer mit eigener Elytra hineinfliegt, kann nur wieder davonfliegen.

## Geister-Elytra
- Nur wer **auf der Insel spawnt** (Erstjoin, Respawn ohne Bett/Anker) ist berechtigt. Hineinfliegen, -macen oder -teleportieren gibt nichts.
- Von der Insel springen und **in der Luft die Leertaste** drücken → man gleitet (wie Vanilla). Man bekommt eine markierte Geister-Elytra (unzerbrechlich, nicht verschieb- oder droppbar), der eigene Brustpanzer wird zwischengespeichert und bei der Landung zurückgegeben.
- **[F]** = Boost wie eine Rakete Stufe 2, alle 5 s.
- Nach **60 s** verliert sie stark an Tragfläche, man sinkt zu Boden.
- **Bodenkontakt**, Wasser, Tod, Teleport, Logout → sofort alles weg. Kein Fall-/Wandaufprallschaden während des Flugs und 3 s danach.
- **Auffangschutz:** Wer nicht gleitet und mit Fallschaden aufschlagen würde (oder ins Void fällt), wird ohne Schaden zum Spawn teleportiert.
- Ausloggen im Flug → beim nächsten Login wieder am Spawn.

## Installation
1. `SpawnElytra.jar` aus dem Release **latest** in den `plugins`-Ordner, Server neu starten.
2. In `server.properties`: `spawn-protection=0`.
3. Ingame (als OP):
   ```
   /se pos1   /se pos2     # zwei gegenüberliegende Ecken der Insel (Luftraum mit einschliessen!)
   /se setspawn            # Spawnpunkt auf der Insel
   /se info
   ```
4. Selbst auf der Insel bauen: `/se bypass` (an/aus). Testen ohne Sterben: im Überlebensmodus auf der Insel `/se test`, abspringen, Leertaste.

Alle Werte (Flugzeit, Boost, Tragflächenverlust, Texte) in `plugins/SpawnElytra/config.yml`, danach `/se reload`.

## Build
GitHub Actions baut bei jedem Push auf `main` und aktualisiert das Release `latest`. Das Build-Log liegt im Branch `ci-logs`.
