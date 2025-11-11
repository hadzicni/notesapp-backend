M450 LB1 – Fehlerprotokoll (nur offene Punkte)

- ID: DFU-001
- Datum: 2025-11-11
- Komponente: DateFormatUtil.parse(String)
- Beschreibung: Eingaben mit führenden/trailing Whitespaces werden nicht akzeptiert.
  Beispiel: " 11.11.2025 22:00 \t" führt zu DateTimeParseException, obwohl das
  erwartete Verhalten ist, dass Leerzeichen ignoriert werden und der Wert korrekt
  geparst wird.
- Reproduktion: Test `parse_should_trim_whitespace` in
  `src/test/java/ch/hadzic/nikola/notesapp/util/DateFormatUtilTest.java`
- Erwartetes Verhalten: `LocalDateTime.of(2025,11,11,22,0)` wird zurückgegeben.
- Aktuelles Verhalten: Ausnahme `DateTimeParseException` (Parsing fehlschlägt).
- Korrekturmaßnahme: `value = value.trim();` vor dem Parsen anwenden.
- Status: Offen
