# Lexico-Syntactic Pattern Extractor
Tool to automatically extract the most generic lexico-syntactic patterns from scientific texts' corpora on English.

Uses GATE Embedded, expects GATE's OpenNLP pipeline as .gapp with OpenNLP Parser step included.

Extraction process:

* Document preprocessing (normalization, brackets removal, etc.)
* OpenNLP Parser syntactic tree rebuilder from GATE annotations
* Distribution calculation
