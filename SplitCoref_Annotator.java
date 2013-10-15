package amb.uima.annotator.corenlp;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.ResourceInitializationException;

import dev.amb.uima.typeSystem.language.Sentence;
import dev.amb.uima.typeSystem.language.WordToken;
import dev.amb.uima.typeSystem.semantic.Entity;
import edu.stanford.nlp.dcoref.Constants;
import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations.CorefChainAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetEndAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.DefaultPaths;
import edu.stanford.nlp.pipeline.DeterministicCorefAnnotator;
import edu.stanford.nlp.pipeline.MorphaAnnotator;
import edu.stanford.nlp.pipeline.ParserAnnotator;
import edu.stanford.nlp.util.CoreMap;

public class SplitCoref_Annotator extends JCasAnnotator_ImplBase {



	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);
	}

	@Override
	public void process(JCas jcas) throws AnalysisEngineProcessException {
		try {

			// create an empty Annotation just with the given text
			Annotation document = this.createRequiredAnnotation(jcas);
			//getContext().getLogger().log(Level.INFO, getClass().getSimpleName() + " :: completed creation of sentence and word token conversion for CoreNLP.");
			System.out.println( "\tINFO: " + getClass().getSimpleName() + " :: completed creation of sentence and word token conversion for CoreNLP.");
			
			// FAQ says we need lemma - could add to the tokens
			MorphaAnnotator lemma = this.createLemmaAnnotator();
			System.out.println( "\tINFO: " + getClass().getSimpleName() + " :: completed word lemmatization");
			lemma.annotate(document);
			
			// Need to parse doc content
			ParserAnnotator parse = this.createTreeParser();
			parse.annotate(document);
			parse = null;
			System.out.println( "\tINFO: " + getClass().getSimpleName() + " :: completed tree parsing using CoreNLP");
			
			DeterministicCorefAnnotator coref = this.createCorefAnnotator();
			coref.annotate(document);
			coref = null;
			System.out.println("\nINFO: " + getClass().getSimpleName() + " :: completed split-up CoreNLP Coreference processing.");

			//getContext().getLogger().log(Level.INFO, getClass().getSimpleName() + " :: completed split-up CoreNLP Coreference processing.  Even added results to CAS index!");
			Map<Integer, CorefChain> graph = document.get(CorefChainAnnotation.class);
			for(int key : graph.keySet()) {
				CorefChain chain = graph.get(key);
				System.out.println("Coref#" + key + " = " + chain.getMentionsInTextualOrder());
			}
			
			
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
				wordLabel.set(CoreAnnotations.NamedEntityTagAnnotation.class , this.getWordEntityTag(word, jcas));

				// TODO do we need lemma?
				// fixme add parse tree

				sentWords.add(wordLabel);

			}

			sentence.set(CoreAnnotations.TokensAnnotation.class, sentWords);
			sentence.set(CoreAnnotations.SentenceIndexAnnotation.class, sent.getDocumentOrder());

			sentLabels.add(sentence);
		}

		doc.set(CoreAnnotations.SentencesAnnotation.class, sentLabels);
		return doc;
	}


	private ParserAnnotator createTreeParser() {
		Properties properties = new Properties();

		properties.put("parse.type" , "stanford");
		properties.put("parse.maxlen" , "500");

		String parserType = properties.getProperty("parse.type", "stanford");
		String maxLenStr = properties.getProperty("parse.maxlen");

		ParserAnnotator anno = new ParserAnnotator("parse", properties);

		return anno;
	}
	
	private MorphaAnnotator createLemmaAnnotator() {
		return new MorphaAnnotator(false);
	}


	private DeterministicCorefAnnotator createCorefAnnotator() {

		Properties props = new Properties(); // blank so we get defaults


		props.setProperty(Constants.DEMONYM_PROP, DefaultPaths.DEFAULT_DCOREF_DEMONYM);
		props.setProperty(Constants.ANIMATE_PROP, DefaultPaths.DEFAULT_DCOREF_ANIMATE);
		props.setProperty(Constants.INANIMATE_PROP, DefaultPaths.DEFAULT_DCOREF_INANIMATE);
		props.setProperty(Constants.MALE_PROP, DefaultPaths.DEFAULT_DCOREF_MALE);
		props.setProperty(Constants.NEUTRAL_PROP, DefaultPaths.DEFAULT_DCOREF_NEUTRAL);
		props.setProperty(Constants.FEMALE_PROP, DefaultPaths.DEFAULT_DCOREF_FEMALE);
		props.setProperty(Constants.PLURAL_PROP, DefaultPaths.DEFAULT_DCOREF_PLURAL);
		props.setProperty(Constants.SINGULAR_PROP, DefaultPaths.DEFAULT_DCOREF_SINGULAR);
		props.setProperty(Constants.STATES_PROP, DefaultPaths.DEFAULT_DCOREF_STATES);
		props.setProperty(Constants.GENDER_NUMBER_PROP, DefaultPaths.DEFAULT_DCOREF_GENDER_NUMBER);
		props.setProperty(Constants.COUNTRIES_PROP, DefaultPaths.DEFAULT_DCOREF_COUNTRIES);
		props.setProperty(Constants.STATES_PROVINCES_PROP, DefaultPaths.DEFAULT_DCOREF_STATES_AND_PROVINCES);
		props.setProperty(Constants.EXTRA_GENDER_PROP, DefaultPaths.DEFAULT_DCOREF_EXTRA_GENDER);
		props.setProperty(Constants.MAXDIST_PROP, "-1");
		
		// TODO toggle me!
		props.setProperty(Constants.BIG_GENDER_NUMBER_PROP, "false");
		props.setProperty(Constants.REPLICATECONLL_PROP, "false");
		props.setProperty(Constants.POSTPROCESSING_PROP, "true");
		props.setProperty(Constants.OPTIMIZE_SIEVES_PROP, "true");
//		props.setProperty(Constants.SIEVES_PROP, "AliasMatch"); WordNet no longer included/used - see https://mailman.stanford.edu/pipermail/java-nlp-user/2012-May/002130.html
		
		
		return new DeterministicCorefAnnotator(props);

	}


	private String getWordEntityTag(WordToken word , JCas jcas) {
		// TODO Likely to need some CoreNLP tag validator
		String tag = "";

		Collection<Entity> coveringEntities = JCasUtil.selectCovering(Entity.class, word);

		if(coveringEntities == null || coveringEntities.size() == 0) {
			tag = "O";
		} else {
			Iterator<Entity> ents = coveringEntities.iterator();
			if(ents.hasNext() == false) {
				tag = "O";
			} else {
				// just get the first covering entity.. we can only assign one tag :)
				tag = ents.next().getEntityType();
			}
		}
		return tag;
	}

}