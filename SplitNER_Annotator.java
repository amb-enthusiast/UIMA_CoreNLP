package amb.uima.annotator.corenlp;


import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;

import dev.amb.uima.typeSystem.language.Sentence;
import dev.amb.uima.typeSystem.language.WordToken;
import dev.amb.uima.typeSystem.semantic.Entity;
import dev.amb.uima.typeSystem.semantic.Location;
import dev.amb.uima.typeSystem.semantic.Organisation;
import dev.amb.uima.typeSystem.semantic.Person;
import edu.stanford.nlp.ie.NERClassifierCombiner;
import edu.stanford.nlp.ie.regexp.NumberSequenceClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetEndAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.DefaultPaths;
import edu.stanford.nlp.pipeline.NERCombinerAnnotator;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;

public class SplitNER_Annotator extends JCasAnnotator_ImplBase {



	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);
	}

	@Override
	public void process(JCas jcas) throws AnalysisEngineProcessException {
		try {

			// create an empty Annotation just with the given text
			Annotation document = this.createRequiredAnnotation(jcas);
			getContext().getLogger().log(Level.INFO, getClass().getSimpleName() + " :: completed creation of sentence and word token conversion for CoreNLP.");

			NERCombinerAnnotator ner = this.createNERAnnotator();
			ner.annotate(document);

			for (CoreMap sentence: document.get(CoreAnnotations.SentencesAnnotation.class)) { 

				List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
				int numWordsInSent = tokens.size();

				for (int tokenIdx = 0; tokenIdx < numWordsInSent ; tokenIdx++) {

					CoreLabel word = tokens.get(tokenIdx);
					String nerType = word.ner();

					// check NER labels and construct NERs from tag sequence
					if(nerType.equals("O") == false) {
						// skip over entity tokens
						tokenIdx = this.createAndIndexEntity(nerType , tokenIdx, tokens, jcas);
					}
				}
			}

			getContext().getLogger().log(Level.INFO, getClass().getSimpleName() + " :: completed split-up CoreNLP NER processing.  Even added results to CAS index!");

		} catch(Exception e) {
			e.printStackTrace();
		}
	}


	private Annotation createRequiredAnnotation(JCas jcas) {

		Annotation doc = new Annotation(jcas.getDocumentText());

		// get sentences from CAS
		Collection<Sentence> sentCollection = JCasUtil.select(jcas, Sentence.class);
		int numSentences = sentCollection.size();

		Iterator<Sentence> sentences = sentCollection.iterator();
		sentCollection = null;

		System.out.println("\nINFO:: " + getClass().getSimpleName() + " :: retreived " + numSentences + " sents from CAS index");

		List<CoreMap> sentLabels = new ArrayList<CoreMap>();

		while(sentences.hasNext() == true) {
			Sentence sent = sentences.next();

			FSArray words = sent.getWordTokens();
			System.out.println("\tINFO:: " + getClass().getSimpleName() + " :: retreived " + words.size() + " words from sentence " + sent.getDocumentOrder() + " from CAS index");

			Annotation sentence = new Annotation(sent.getCoveredText());
			sentence.set(CharacterOffsetBeginAnnotation.class, sent.getBegin());
			sentence.set(CharacterOffsetEndAnnotation.class, sent.getEnd());


			ArrayList<CoreLabel> sentWords = new ArrayList<CoreLabel>();

			for(int wordIdx = 0; wordIdx < words.size();wordIdx++) {
				WordToken word = (WordToken) words.get(wordIdx);
				// now for this sent, create words

				CoreLabel wordLabel = new CoreLabel();

				// Use annotation class names to put properties in a map
				wordLabel.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class , word.getBegin());
				wordLabel.set(CoreAnnotations.CharacterOffsetEndAnnotation.class , word.getEnd());
				wordLabel.set(CoreAnnotations.TextAnnotation.class, word.getCoveredText());
				wordLabel.set(CoreAnnotations.PartOfSpeechAnnotation.class, word.getPartOfSpeech());
				wordLabel.set(CoreAnnotations.TokenBeginAnnotation.class , wordIdx);
				wordLabel.set(CoreAnnotations.SentenceIndexAnnotation.class, sent.getDocumentOrder());

				sentWords.add(wordLabel);

			}

			sentence.set(CoreAnnotations.TokensAnnotation.class, sentWords);
			sentence.set(CoreAnnotations.SentenceIndexAnnotation.class, sent.getDocumentOrder());

			sentLabels.add(sentence);
		}

		doc.set(CoreAnnotations.SentencesAnnotation.class, sentLabels);
		return doc;
	}

	private NERCombinerAnnotator createNERAnnotator() {

		Properties properties = new Properties(); // blank so we get defaults

		// use the Stanford models
		List<String> models = new ArrayList<String>();
		models.add(DefaultPaths.DEFAULT_NER_THREECLASS_MODEL);
		models.add(DefaultPaths.DEFAULT_NER_MUC_MODEL);
		models.add(DefaultPaths.DEFAULT_NER_CONLL_MODEL);

		if (models.isEmpty()) {
			// Allow for no real NER model - can just use numeric classifiers or SUTime
			// Will have to explicitly unset ner.model.3class, ner.model.7class, ner.model.MISCclass
			// So unlikely that people got here by accident
			System.err.println("WARNING: no NER models specified");
		}

		NERClassifierCombiner nerCombiner = null;

		try {

			boolean applyNumericClassifiers =	PropertiesUtils.getBool(properties,
					NERClassifierCombiner.APPLY_NUMERIC_CLASSIFIERS_PROPERTY,
					true);

			boolean useSUTime = PropertiesUtils.getBool(properties,
					NumberSequenceClassifier.USE_SUTIME_PROPERTY,
					true);

			nerCombiner = new NERClassifierCombiner(applyNumericClassifiers,
					useSUTime, properties,
					models.toArray(new String[models.size()]));

		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}

		return new NERCombinerAnnotator(nerCombiner, false);
	}

	private int createAndIndexEntity(String nerTag, int startTokenIdx, List<CoreLabel> sentenceTokens, JCas jcas) {

		Entity ent = this.createEntityFromTag(nerTag, jcas);
		
		int entEndIdx = 0;
		
		for(int entIdx = startTokenIdx; entIdx < sentenceTokens.size(); entIdx++) {

			// check subsequent tokens
			if(nerTag.equals(sentenceTokens.get(entIdx).ner()) == false) {
				entEndIdx = entIdx;
				break;
			}
		}
		
		// complete entity def
		
		ent.setBegin(sentenceTokens.get(startTokenIdx).beginPosition());
		ent.setEnd(sentenceTokens.get(entEndIdx - 1).endPosition());
		ent.setEntityType(nerTag);
		ent.addToIndexes();
		
		System.out.println("\tAdded " + ent.getEntityType() + "@[" + ent.getBegin() + " , " + ent.getEnd() + "] to CAS");
		
		return entEndIdx;
	}

	private Entity createEntityFromTag(String nerTag , JCas jcas) {
		System.out.println("\tCreating entity for [" + nerTag + "] tag");
		Entity ent = null;
		if(nerTag.equals("PERSON")) {
			ent = new Person(jcas);
		} else if(nerTag.equals("LOCATION")) {
			ent = new Location(jcas);
		} else if(nerTag.equals("ORGANIZATION")) {
			ent = new Organisation(jcas);
		} else {
			ent = new Entity(jcas);
		}
		return ent;
	}



}