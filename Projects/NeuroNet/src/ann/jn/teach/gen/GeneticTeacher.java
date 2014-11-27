package ann.jn.teach.gen;

import java.util.ArrayList;

import ann.jn.neuroNet.NeuralNet;

/**
 * Uses a genetic algorithm to evolve a {@link NeuralNet}.
 * @author Nicholas Utz
 *
 */
public class GeneticTeacher {
	
	/**
	 * Defines the interface of a class that wishes to use a {@link GeneticTeacher} to
	 * teach a {@link NeuralNet}.
	 * @author Nicholas Utz
	 */
	public interface IGeneticTeacherCallbacks {
		/**
		 * Called when a cycle of evolution is complete and the next generation
		 * of {@link WeightMap}s is ready for selection.
		 */
		public void onGenerationReady();
		
		
	}

	private final IGeneticTeacherCallbacks callbacks;
	
	private final int generationSize;
	
	private final int bufferedGenSize;
	
	private ArrayList<Generation> genBuffer;
	
	private final int maxBufferCount;
	
	private Generation currentGeneration;
	
	private int generationNumber;
	
	private NeuralNet templateNet;
	
	/**
	 * Creates a new GeneticTeacher with <code>genSize</code> individuals per generation,
	 * <code>bufferSize</code> individuals per buffered generation, and <code>bufferCount</code>
	 * buffered generations.
	 * @param genSize the number of individuals per generation
	 * @param bufferSize the number of individuals in each buffered generation
	 * @param bufferCount the number of generations buffered
	 * @param template the NeuralNet to be evolved
	 */
	public GeneticTeacher(int genSize, int bufferSize, int bufferCount, NeuralNet template, IGeneticTeacherCallbacks callbacks) {
		if (genSize <= 0 || bufferSize < 0 || bufferCount < 0) {
			throw new IllegalArgumentException("genSize, bufferSize, and bufferCount must be greater than zero");
			
		} else if (template == null) {
			throw new NullPointerException("template NeuralNet cannot be null");
			
		} else if (bufferSize < genSize) {
			throw new IllegalArgumentException("bufferSize cannot be greater than genSize");
		}
		
		this.generationSize = genSize;
		this.bufferedGenSize = bufferSize;
		this.genBuffer = new ArrayList<Generation>(bufferCount);
		this.maxBufferCount = bufferCount;
		this.templateNet = template;
		this.callbacks = callbacks;
		genRandomVariation();
	}
	
	/**
	 * <p>
	 * Evolves the current generation.
	 * </p>
	 * <p>
	 * Evolution begins with the averaging of the fitnesses of each 'genome' ({@link WeightMap}) so that
	 * they can be sorted by fitness. The fittest individuals are then bread (via {@link #breed(WeightMap, WeightMap)})
	 * and the others are mutated ({@link #mutate(WeightMap)}) in an attempt to produce a fitter individuals
	 * for the next generation.
	 * </p>
	 * <p>
	 * The execution of this function is a very time consuming process. It is suggested that {@link #beginEvolution()} be
	 * used instead to perform the evolution asynchronously.
	 * </p>
	 * <p>
	 * At the conclusion of this method, {@link IGeneticTeacherCallbacks#onGenerationReady()} is 
	 * called on the {@link IGeneticTeacherCallbacks} that was used to create this {@link GeneticTeacher}.
	 * </p>
	 */
	/*
	 * TODO store fitness of previous generation and use to decide whether...
	 * current generation is worth breeding or if old generation should be used again
	 */
	public void doEvolution() {
		ArrayList<WeightMap> individuals = new ArrayList<WeightMap>(generationSize);
		
		synchronized (this) {
			//average fitnesses
			for (int i = 0; i < generationSize; i++) {
				currentGeneration.fitnesses[i] = currentGeneration.fitnesses[i] / currentGeneration.fitnessReports[i];
			}
			
			//order individuals by fitness
			for (int i = generationSize; i > 0; i--) {
				//find individual of greatest fitness
				int greatestFitnessIndex = 0;
				for (int j = 0; j < generationSize; j++) {
					if (currentGeneration.fitnesses[j] > currentGeneration.fitnesses[greatestFitnessIndex] && currentGeneration.fitnessReports[j] != -1) {
						greatestFitnessIndex = j;
					}
				}
				//add individual of greatest fitness to list
				individuals.add(currentGeneration.genomes[greatestFitnessIndex]);
				currentGeneration.fitnessReports[greatestFitnessIndex] = -1;
			}
		}
		
		//breed and optionally mutate to create next generation
		Generation nextGen = new Generation(generationSize);
		int individualsSet = 0;
		
		//breed first half of individuals sorted by fitness
		for (int i = 0; (i < individuals.size() / 2) && (individualsSet < generationSize); i++, individualsSet++) {
			nextGen.genomes[individualsSet] = breed(individuals.get(i), individuals.get(i + 1));
		}
		
		//mutate second half of individuals sorted by order
		for (int i = 0; (i < individuals.size() / 2) && (individualsSet < generationSize); i++, individualsSet++) {
			nextGen.genomes[individualsSet] = mutate(individuals.get(individualsSet));
		}
		
		Generation lastGen = new Generation(bufferedGenSize);
		for (int i = 0; i < bufferedGenSize; i++) {
			lastGen.genomes[i] = individuals.get(i);
		}
		
		synchronized (this) {
			currentGeneration = nextGen;
			genBuffer.add(lastGen);
			if (genBuffer.size() > maxBufferCount) {
				genBuffer.remove(0);
			}
		}
		
		generationNumber++;
		this.callbacks.onGenerationReady();
	}
	
	/**
	 * Calls {@link #doEvolution()} asynchronously. {@link IGeneticTeacherCallbacks#onGenerationReady()}
	 * is called at the end of the call to inform the {@link IGeneticTeacherCallbacks} that created
	 * this {@link GeneticTeacher} to announce when the new generation of {@link WeightMap}s is ready.
	 */
	public void beginEvolution() {
		Thread t = new Thread(new Runnable(){
			@Override
			public void run() {
				doEvolution();
			}
		});
		
		t.start();
	}
	
	/**
	 * Used during initialization to create random variation to be selected on.
	 */
	private void genRandomVariation() {
		currentGeneration = new Generation(generationSize);
		for (int i = 0; i < generationSize; i++) {
			WeightMap map = WeightMapUtils.getWeights(templateNet);
			for (int x = 0; x < map.getNumLayers(); x++) {
				for (int y = 0; i < map.getNumNuronsInLayer(x); y++) {
					float[] weights = map.getWeightsForNeuron(x, y);
					for (int z = 0; z < weights.length; z++) {
						weights[z] *= (float) ((Math.random() * 4) - 2); //multiply weight by between -200% and 200%
					}
				}
			}
		}
	}
	
	/**
	 * Creates a mutation in the given {@link WeightMap}.
	 * @param map1 the set of 'genes' to mutate
	 */
	private WeightMap mutate(WeightMap map) {
		int style = (int) (Math.random() * 3);
		
		switch (style) {
		case 0 : {//random mutation
			//single weight mutations, lets do a lot of them
			int numMutations = (int) (0.85 * map.getNumLayers() * map.getNumNuronsInLayer(1) * Math.random());
			for (int i = 0; i < numMutations; i++) {
				int x = (int) (Math.random() * map.getNumLayers());
				int y = (int) (Math.random() * map.getNumNuronsInLayer(x));
				int z = (int) (Math.random() * map.getNumWeightsForNeuron(x, y));
				
				float[] layer = map.getWeightsForNeuron(x, y);
				layer[z] *= (float) ((Math.random() * 4) - 2);
				map.setWeightsForNeuron(x, y, layer);
			}
			
		} break;
		case 1 : {//swap layers
			//moving complete layers
			int numMutations = (int) (0.25 * map.getNumLayers() * map.getNumNuronsInLayer(1) * Math.random());
			for (int i = 0; i < numMutations; i++) {
				int layerSrc = (int) (Math.random() * map.getNumLayers());
				int layerDst = (int) (Math.random() * map.getNumLayers());
				float[][] trxBuffer = map.getLayer(layerDst);
				map.setLayer(layerDst, map.getLayer(layerSrc));
				map.setLayer(layerSrc, trxBuffer);
			}
			
		} break;
		case 2 : {//swap neurons in layer
			int numMutations = (int) (0.5 * map.getNumLayers() * map.getNumNuronsInLayer(1) * Math.random());
			int targetLayer = (int) (Math.random() * map.getNumLayers());
			for (int i = 0; i < numMutations; i++) {
				int neuronSrc = (int) (Math.random() * map.getNumNuronsInLayer(targetLayer));
				int neuronDst = (int) (Math.random() * map.getNumNuronsInLayer(targetLayer));
				float[] trxBuffer = map.getWeightsForNeuron(targetLayer, neuronDst);
				map.setWeightsForNeuron(targetLayer, neuronDst, map.getWeightsForNeuron(targetLayer, neuronSrc));
				map.setWeightsForNeuron(targetLayer, neuronSrc, trxBuffer);
			}
			
		} break;
		case 3 : {//swap neurons throughout
			int numMutations = (int) (0.75 * map.getNumLayers() * map.getNumNuronsInLayer(1) * Math.random());
			for (int i = 0; i < numMutations; i++) {
				int neuronSrcX = (int) (Math.random() * map.getNumLayers());
				int neuronSrcY = (int) (Math.random() * map.getNumNuronsInLayer(neuronSrcX));
				
				int neuronDstX = (int) (Math.random() * map.getNumLayers());
				int neuronDstY = (int) (Math.random() * map.getNumNuronsInLayer(neuronDstX));
				
				float[] trxBuffer = map.getWeightsForNeuron(neuronDstX, neuronDstY);
				map.setWeightsForNeuron(neuronDstX, neuronDstY, map.getWeightsForNeuron(neuronSrcX, neuronSrcY));
				map.setWeightsForNeuron(neuronSrcX, neuronSrcY, trxBuffer);
			}
			
		} break;
		}
		
		return map;
	}
	
	/**
	 * 'Breeds' the two {@link WeightMap}s given, intermixing their genes. Breeding is used to attempt to
	 * combine the 'good' genes of two individuals.
	 * @param map1 the first set of genes to breed
	 * @param map2 the second set of genes to breed
	 */
	private WeightMap breed(WeightMap map1, WeightMap map2) {
		WeightMap resMap = WeightMapUtils.getWeights(templateNet);
		int style = (int) (Math.random() * 3);
		
		switch (style) {
		case 0 : {//every other layer
			for (int l = 0; l < resMap.getNumLayers(); l++) {
				if (Math.random() < 0.5) {
					resMap.setLayer(l, map1.getLayer(l));
					
				} else {
					resMap.setLayer(l, map2.getLayer(l));
				}
			}
			
		} break;
		case 1 : {//half every layer
			for (int x = 0; x < resMap.getNumLayers(); x++) {
				for (int y = 0; y < Math.floor(resMap.getNumNuronsInLayer(x) / 2); y++) {
					resMap.setWeightsForNeuron(x, y, map1.getWeightsForNeuron(x, y));
				}
				
				for (int y = (int) Math.ceil(resMap.getNumNuronsInLayer(x) / 2); y < resMap.getNumNuronsInLayer(x); y++) {
					resMap.setWeightsForNeuron(x, y, map2.getWeightsForNeuron(x, y));
				}
			}
			
		} break;
		case 3 : {//every other neuron
			boolean m1 = true;
			for (int x = 0; x < resMap.getNumLayers(); x++) {
				for (int y = 0; y < resMap.getNumNuronsInLayer(x); y++) {
					if (m1) {
						resMap.setWeightsForNeuron(x, y, map1.getWeightsForNeuron(x, y));
						
					} else {
						resMap.setWeightsForNeuron(x, y, map1.getWeightsForNeuron(x, y));
					}
				}
			}
		}
		}
		
		return resMap;
	}
	
	/**
	 * <p>
	 * Records a fitness rating for the given {@link WeightMap}. Fitness is only recorded if the 
	 * given WeightMap is part of the current generation.
	 * </p>
	 * <p>
	 * When a generation is declared over via a call to {@link } the average fitness of each WeightMap is
	 * compared to those of the other WeightMaps in the same and previous generations to decide which
	 * WeightMaps are will be bread to produce the next generation.
	 * </p>
	 * @param fitness the fitness rating for the given WeightMap
	 * @param map the WeightMap whose fitness is being reported
	 */
	public synchronized void recordFitness(int fitness, WeightMap map) {
		for (int i = 0; i < currentGeneration.genomes.length; i++) {
			if (currentGeneration.genomes[i].equals(map)) {
				if (currentGeneration.fitnessReports[i] == -1) {
					return;
				}
				currentGeneration.fitnesses[i] += fitness;
				currentGeneration.fitnessReports[i]++;
			}
		}
	}

	/**
	 * Returns the number of different {@link WeightMap}s in the current generation.
	 * @return number of maps in generation
	 */
	public synchronized int getNumMaps() {
		return currentGeneration.genomes.length;
	}
	
	/**
	 * <p>
	 * Retrieves and returns a {@link WeightMap} from the current generation.
	 * </p>
	 * <p>
	 * The order in which WeightMaps are stored should correlate to relative fitness but due to the
	 * evolution process, this sorting is not guaranteed.
	 * </p>
	 * @param index the index of the map to retrieve, must be greater than zero and less than the result of {@link #getNumMaps()}
	 * @return the WeightMap from the current generation with the given index
	 */
	public synchronized WeightMap getMap(int index) {
		return currentGeneration.genomes[index];
	}
}

class Generation {
	protected WeightMap[] genomes;
	protected int[] fitnesses;
	protected int[] fitnessReports;
	
	public Generation(int size) {
		this.genomes = new WeightMap[size];
		this.fitnesses = new int[size];
		this.fitnessReports = new int[size];
	}
}