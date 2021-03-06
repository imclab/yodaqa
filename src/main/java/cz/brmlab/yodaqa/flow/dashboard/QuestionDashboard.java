package cz.brmlab.yodaqa.flow.dashboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import cz.brmlab.yodaqa.model.Question.QuestionInfo;

/** A question tracking device.  This singleton class is used to keep
 * track of all questions posed and possibly answered so far, record
 * progress on answering them, and so on.  This is not useful for
 * the basic io modalities (like interactive or tsvgs), but gets
 * relevant for the web io where we can query the question answering
 * state asynchronously. */
/* N.B. this class must be thread-safe. */
public final class QuestionDashboard {
	/* Singleton. */
	private static QuestionDashboard qd = new QuestionDashboard();
	private QuestionDashboard() {}
	public static QuestionDashboard getInstance() {
		return qd;
	}

	/* All asked questions, by their id. */
	private Map<Integer, Question> questions = new HashMap<>();
	/* Questions that were not passed to the pipeline yet.
	 * .wait() and .notify() are used for signalization
	 * of any changes here. */
	/* XXX: This could be also a queue.  But we might want to do
	 * something smarter than FIFO in case of many questions, e.g.
	 * preferring questions from different remote hosts. */
	private List<Question> questionsToAnswer = new LinkedList<>();
	/* Questions that are currently being answered. */
	private List<Question> questionsInProgress = new LinkedList<>();
	/* Questions that have been answered, last coming first. */
	private List<Question> questionsAnswered = new LinkedList<>();

	public synchronized void askQuestion(Question q) {
		questions.put(q.getId(), q);
		questionsToAnswer.add(q);
		notify();
	}

	/** Get a question to answer by the pipeline.  Blocks in case
	 * there isn't any right now. */
	public synchronized Question getQuestionToAnswer() {
		while (questionsToAnswer.isEmpty()) {
			try { wait(); } catch (InterruptedException e) { }
		}
		Question q = questionsToAnswer.iterator().next();
		questionsToAnswer.remove(q);
		questionsInProgress.add(q);
		return q;
	}

	public synchronized Question get(int id) {
		return questions.get(id);
	}
	public Question get(JCas questionCas) {
		QuestionInfo qi = JCasUtil.selectSingle(questionCas, QuestionInfo.class);
		return get(Integer.parseInt(qi.getQuestionId()));
	}

	public synchronized void finishQuestion(Question q) {
		q.setFinished(true);
		questionsInProgress.remove(q);
		questionsAnswered.add(0, q);
	}

	public synchronized List<Question> getQuestionsToAnswer() { return new ArrayList<>(questionsToAnswer); }
	public synchronized List<Question> getQuestionsInProgress() { return new ArrayList<>(questionsInProgress); }
	public synchronized List<Question> getQuestionsAnswered() { return new ArrayList<>(questionsAnswered); }
};
