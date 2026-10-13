# SpawnElytra

Paper-Plugin (26.2) für den Spawn:

- **Geister-Elytra**: Wer im Absprungbereich *erscheint* (Join, Respawn ohne Bett/Anker) und den Bereich **in der Luft** verlässt, gleitet mit einer Elytra, die nur sein Client sieht – es gibt kein echtes Item.
  - **[F]** = Boost wie eine Rakete Stufe 2, alle 5 s
  - nach **60 s** verliert sie stark an Tragfläche, man sinkt zu Boden
  - **Bodenkontakt**, Wasser, Tod, Teleport, Logout → sofort alles weg
  - Wer in den Bereich hineinfliegt oder -läuft, bekommt **nichts** (kein Endlos-Flug mit eigener Elytra/Mace)
  - Kein Fall- und Wandaufprallschaden während des Flugs und 3 s danach
- **Eigener Spawnschutz** im Schutzbereich (standardmässig über die ganze Höhe): kein Schaden, kein Hunger, feindliche Mobs werden entfernt, kein Bauen/Abbauen, kein PvP, keine Gegenstände (Raketen, Windkugeln, Perlen, Bögen …), keine Truhen/Türen/Knöpfe, kein Mob-Spawning, keine Explosionen, kein Feuer, keine Flüssigkeiten/Kolben von aussen. Essen ist erlaubt.

## Installation

1. `SpawnElytra.jar` aus dem Release **latest** in den `plugins`-Ordner, Server neu starten.
2. In `server.properties`: `spawn-protection=0` (Vanilla-Spawnschutz aus).
3. Ingame (als OP):
   ```
   /se pos1 launch     /se pos2 launch      # Absprungbereich
   /se pos1 protect    /se pos2 protect     # Spawnschutz
   /se setspawn                             # Spawnpunkt (muss im Absprungbereich liegen)
   /se info
   ```
4. Selbst im Spawn bauen: `/se bypass` (an/aus). Testflug ohne Sterben: im Absprungbereich `/se test`, dann abspringen.

Alle Werte (Flugzeit, Boost, Tragflächenverlust, Texte) stehen in `plugins/SpawnElytra/config.yml`, danach `/se reload`.

## Build

GitHub Actions baut bei jedem Push und aktualisiert das Release `latest`. Das Build-Log liegt im Branch `ci-logs`.
