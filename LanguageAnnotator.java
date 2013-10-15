package dev.amb.uima.annotator;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;

import dev.amb.uima.typeSystem.language.Sentence;
import dev.amb.uima.typeSystem.language.WordToken;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

/*
 * First attempt to create a CoreNLP pipeline just for Sentence, WordToken (with POS tags) and index into CAS
 */
public class LanguageAnnotator extends JCasAnnotator_ImplBase {
	
	private StanfordCoreNLP pipeline = null ;
	
	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);
		
		// create CoreNLP pipeline for sentence, word token and POS tags
		Properties langProps = new Properties();
		langProps.put("annotators", "tokenize, ssplit, pos");
		
		this.pipeline = new StanfordCoreNLP(langProps);
		
	}
	
	@Override
	public void process(JCas jcas) throws AnalysisEngineProcessException {
		try {
			
			String text = jcas.getDocumentText();
			
			// create an empty Annotation just with the given text
			Annotation document = new Annotation(text);

			// run all Annotators on this text
			pipeline.annotate(document);
			
			List<CoreMap> sents = document.get(SentencesAnnotation.class);
			
			System.out.println("\nFound total of " + sents.size() + " sents in doc from CoreNLP.");
			
			for(int sentIdx = 0; sentIdx < sents.size(); sentIdx++) {
				
				System.out.println("\nINFO:  processing sentence#" + sentIdx);
				CoreMap sentMap = sents.get(sentIdx);
				
				Sentence sent = new Sentence(jcas);
				
				int sentStart = 0;
				int sentEnd = 0;
				
				List<CoreLabel> sentWords = sentMap.get(TokensAnnotation.class);
				
				boolean sentBegin = true;
				
				// Array to store words in sentence, in order of occurrence in doc
				
				FSArray wordArray = new FSArray(jcas, sentWords.size());
				
				for(int idx = 0; idx < sentWords.size(); idx++) {
					
					CoreLabel token = (CoreLabel) sentWords.get(idx);
					
					String pos = token.get(PartOfSpeechAnnotation.class);
					int wordStart= token.beginPosition();
					int wordEnd = token.endPosition();
					
					// accumulate values from word tokens about sentence start, end and index in document
					if(sentBegin == true) {
						sentStart = wordStart;
						sentBegin = false;
					}
					if(wordEnd > sentEnd) {
						sentEnd = wordEnd;
					}
					
					WordToken word = new WordToken(jcas);
					word.setBegin(wordStart);
					word.setEnd(wordEnd);
					word.setPartOfSpeech(pos);
					word.setSentenceOrder(idx);
					word.addToIndexes();
					
					wordArray.set(idx, word);
				}
				
				wordArray.addToIndexes();
				
				sent.setBegin(sentStart);
				sent.setEnd(sentEnd);
				sent.setDocumentOrder(sentIdx);
				sent.setWordTokens(wordArray);
				
				sent.addToIndexes();
				
			}
			
		} catch(Exception e) {
			getContext().getLogger().log(Level.WARNING , getClass().getSimpleName() + " exception :: " + e.getMessage());
			throw new AnalysisEngineProcessException(e);
		}
		
	}

}
