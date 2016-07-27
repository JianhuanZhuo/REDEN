package fr.ign.georeden.algorithms.graph.matching;

/**
 * The Class CriterionToponymCandidate. Represents the score of toponym's candidate.
 */
public class CriterionToponymCandidate {
	private float value;
	private Candidate candidate;
	private Toponym toponym;
	private Criterion criterion;
	
	/**
	 * Instantiates a new score for the specified criterion between a toponym and a candidate.
	 *
	 * @param toponym the toponym
	 * @param candidate the candidate
	 * @param value the value
	 * @param criterion the criterion
	 */
	public CriterionToponymCandidate(Toponym toponym, Candidate candidate, float value, Criterion criterion) {
		this.toponym = toponym;
		this.candidate = candidate;
		this.value = value;
		this.criterion = criterion;
	}
	
	public Criterion getCriterion() {
		return this.criterion;
	}
	public Toponym getToponym() {
		return this.toponym;
	}
	public Candidate getCandidate() {
		return this.candidate;
	}
	public float getValue() {
		return this.value;
	}
}