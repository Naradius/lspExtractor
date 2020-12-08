# Lexico-Syntactic Pattern Extractor
Tool to automatically extract the most generic lexico-syntactic patterns from scientific texts on English.

Uses GATE Embedded, expects GATE's OpenNLP pipeline as .gapp with OpenNLP Parser step included.

Extraction process:

* Document preprocessing (normalization, brackets removal, etc.)
* Syntactic tree rebuilding from GATE annotations
* Syntactic tree optimization
* Phrases distribution calculation
