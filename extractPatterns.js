function extractPatterns(documents) {
  let constructions = [];
	let annotatedSentences = preprocess(documents);
	annotatedSentences.forEach((sentence) => {
    let parseTree = rebuildParseTree(sentence);
    pruneAndSplitParseTree(parseTree, constructions);    
  });
  let distribution = [];
  calculateDistribution(constructions, distribution);
  let filteredConstructions = filterConstructions(distribution);
  let patterns = generatePatterns(filteredConstructions);

  return patterns;
}









