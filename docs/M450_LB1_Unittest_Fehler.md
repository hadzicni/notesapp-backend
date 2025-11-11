M450 LB1 – Fehlerprotokoll

- ID: BUG-001
- Datum: 2025-11-11
- Komponente: NoteService.updateNote
- Beschreibung: Beim Aktualisieren der Tags wurde die Note vor dem Setzen der neuen Tag-Menge (`existing.setTags(...)`) in die `notes`-Mengen der Tags eingefügt. Da `Note` `equals/hashCode` (via Lombok @EqualsAndHashCode) über mutable Felder (u.a. `tags`) definiert, führte das spätere Mutieren der `tags`-Menge zu einer HashCode-Änderung. Das bewirkt, dass die Note im `HashSet` der Tags nicht mehr zuverlässig gefunden wird.
- Reproduktion: Unit-Test `src/test/java/ch/hadzic/nikola/notesapp/service/NoteServiceTest.java:144` (Methode `updateNote_withTags_replacesAndMaintainsBidirectionalLinks`) deckt das Problem ab, indem er die bidirektionalen Verknüpfungen prüft.
- Auswirkung: Die bidirektionale Beziehung Tag.notes enthielt die Note nicht (fehlender Link), obwohl dies erwartet wurde.
- Ursache (Root Cause): Reihenfolge der Operationen + mutable equals/hashCode.
- Massnahme: Zuerst `existing.setTags(persistentTags)` ausführen, danach die Note den `notes`-Mengen der Tags hinzufügen. Zusätzlich Null-Check auf `persistentTag.getNotes()`.
- Status: Behoben in `src/main/java/ch/hadzic/nikola/notesapp/data/service/NoteService.java:98-107`.

