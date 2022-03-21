# Lexico-Syntactic Pattern Extractor
Tool to automatically extract the most generic lexico-syntactic patterns from scientific texts on English.

Uses GATE Embedded, expects GATE's OpenNLP pipeline as .gapp with OpenNLP Parser step included.

Extraction process:

* Document preprocessing (normalization, brackets removal, etc.)
* Pipeline execution
* Syntactic trees rebuilding from GATE annotations
* Syntactic trees generalization
* Extracted phrases distribution calculation
* (TODO) Phrase filtering
* (TODO) JAPE pattern generation
