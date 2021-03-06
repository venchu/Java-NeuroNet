package ann.jn.neuroNet;

/**
 * The core of the Artificial neural network.  The NeuralNet
 * controls the creation and updates of the network.
 */
public class NeuralNet {
	private Neuron.INeuronActivationFunction func;
	private Neuron[][] net;
	private Object updateLock;
	private volatile boolean updating;
	private volatile int threads;

	private float[] storedInputs;

	/**
	 * Creates a NeuralNet with the specified number of neurons in each layer.
	 * The first listed layer is the number of input neurons, and the last layer
	 * is the number of output neurons.
	 * 
	 * for example, <code>NeuralNet(3, 4, 1)<code>
	 * will create a NeuralNet with 3 input neurons, 4 hidden neurons,
	 * and 1 output neuron.
	 * 
	 * @param layers the number neurons each layer should have.
	 */
	public NeuralNet(int... layers) {
		this(null, layers);
	}
	
	/**
	 * Creates a NeuralNet with the specified number of neurons in each layer, and
	 * the specified neuron activation, which defaults to a logistic function.
	 * The first listed layer is the number of input neurons, and the last layer
	 * is the number of output neurons.
	 * 
	 * for example, <code>NeuralNet(3, 4, 1)<code>
	 * will create a NeuralNet with 3 input neurons, 4 hidden neurons,
	 * and 1 output neuron.
	 * 
	 * @param layers the number neurons each layer should have.
	 */
	public NeuralNet(Neuron.INeuronActivationFunction func, int... layers) {
		if (layers == null || layers.length < 2) {
			throw new IllegalArgumentException("Invalid layers!");
		}

		// Create the net
		net = new Neuron[layers.length][];
		for (int i = 0; i < net.length; i++) {
			net[i] = new Neuron[layers[i]];
			
			for (int j = 0; j < net[i].length; j++) {
				int ins = i == 0 ? 1 : layers[i - 1];
				
				if (func != null) {
					net[i][j] = new Neuron(ins, func);
				} else {
					net[i][j] = new Neuron(ins);
				}
			}
		}

		this.func = func;
		storedInputs = new float[layers[0]];
		updateLock = new Object();
		updating = false;
		threads = 0;
	}

	/**
	 * Randomizes the weights of each input for each Neuron.
	 * Each input is between 0 and 1.
	 */
	public void randomizeWeights() {
		synchronized (updateLock) {
			for (int i = 0; i < 0; i++) {
				net[0][i].setWeights(new float[]{rand()});
				net[0][i].setBias(rand());
			}
			
			for (int layer = 1; layer < net.length; layer++) {
				for (int i = 0; i < net[layer].length; i++) {
					float[] weights = new float[net[layer - 1].length];
					
					for (int j = 0; j < weights.length; j++) {
						weights[j] = rand();
					}
					
					net[layer][i].setWeights(weights);
					net[layer][i].setBias(rand());
				}
			}
		}
	}
	
	private float rand() {
		return (float) (Math.random());
	}

	/**
	 * Sets the weight of each input to zero for each Neuron.
	 */
	public void zeroWeights() {
		synchronized (updateLock) {
			for (int i = 0; i < 0; i++) {
				net[0][i].setWeights(new float[]{0});
				net[0][i].setBias(0);
			}
			
			for (int layer = 1; layer < net.length; layer++) {
				for (int i = 0; i < net[layer].length; i++) {
					float[] weights = new float[net[layer - 1].length];
					net[layer][i].setWeights(weights);
					net[layer][i].setBias(0);
				}
			}
		}
	}

	/**
	 * Sets the input of the specified neuron on the input
	 * layer.  This has no effect on the weights or inputs
	 * of other neurons until update() is called.
	 * 
	 * @param input the number of the neuron
	 * @param value the value to set it to
	 */
	public void setInput(int input, float value) {
		if (input >= storedInputs.length) {
			throw new IllegalArgumentException("Invalid neuron number!");
		}

		synchronized (updateLock) {
			storedInputs[input] = value;
		}
	}

	/**
	 * Sets the inputs for this neural network. This has no effect
	 * on the weights or inputs of other neurons until update() is called.
	 * 
	 * @param value the values for each input neuron.
	 */
	public void setInputs(float[] value) {
		if (value == null || value.length != net[0].length) {
			throw new IllegalArgumentException("Invalid value length!");
		}

		synchronized (updateLock) {
			for (int i = 0; i < storedInputs.length; i++) {
				storedInputs[i] = value[i];
			}
		}
	}

	/**
	 * Updates the neural network and returns the output's values.
	 * 
	 * @return the outputs
	 */
	public float[] update() {
		doUpdate();

		return getOutputs();
	}

	/**
	 * Updates the neural network and returns the output's values.
	 * Additionally, it calls the specified callback when done.
	 * 
	 * @param cb the callback
	 * @return the outputs
	 */
	public float[] update(INeuralNetCallback cb) {
		doUpdate();
		cb.onFinish();

		return getOutputs();
	}

	/**
	 * Updates the neural network on a different thread.
	 */
	public void updateAsync() {
		new NeuralNetUpdateThread(null);
	}

	/**
	 * Updates the neural network on a different thread and
	 * calls the specified callback when done.
	 * 
	 * @param cb the callback
	 */
	public void updateAsync(INeuralNetCallback cb) {
		new NeuralNetUpdateThread(cb);
	}

	/**
	 * Determines whether the neural network is currently updating.
	 * 
	 * @return whether the neural network is updating
	 */
	public boolean isUpdating() {
		return updating;
	}
	
	/**
	 * Gets an input
	 * 
	 * @param input the input number
	 * @return the input value
	 */
	public float getInput(int input) {
		if (input >= net[0].length) {
			throw new IllegalArgumentException("Neuron number is invalid");
		}
		
		return (storedInputs[input]);
	}
	
	/**
	 * Gets all of the inputs.
	 * 
	 * @return the inputs
	 */
	public float[] getInputs() {
		return (storedInputs);
	}

	/**
	 * Gets the last output for the specified neuron.
	 * 
	 * @param output the output number
	 * @return the neuron's output
	 */
	public float getOutput(int output) {
		if (output >= net[net.length - 1].length) {
			throw new IllegalArgumentException("Neuron number is invalid");
		}
		
		synchronized (updateLock) {
			return (net[net.length - 1][output].getOutput());
		}
	}

	/**
	 * Gets the last outputs in the neural network.
	 * 
	 * @return an array of outputs.
	 */
	public float[] getOutputs() {
		synchronized (updateLock) {
			float[] outs = new float[net[net.length - 1].length];
			
			for (int i = 0; i < outs.length; i++) {
				outs[i] = net[net.length - 1][i].getOutput();
			}
			
			return (outs);
		}
	}
	
	/**
	 * Returns the number of neural layers in this net.
	 * 
	 * @return the number of layers
	 */
	public int getNumLayers() {
		return (net.length);
	}

	/**
	 * Gets an array consisting of all the neurons in the specified layer.
	 * 
	 * @param layer the layer
	 * @return the neurons
	 */
	public Neuron[] getLayer(int layer) {
		if (layer >= net.length) {
			throw new IllegalArgumentException("The layer doesn't exist");
		}
		
		return net[layer];
	}

	/**
	 * Gets the neuron at the specified layer and position.
	 * 
	 * @param layer the layer
	 * @param num the number of the neuron
	 * @return the neuron
	 */
	public Neuron getNeuron(int layer, int num) {
		if (layer >= net.length) {
			throw new IllegalArgumentException("The neuron doesn't exist");
		}
		
		return net[layer][num];
	}

	/**
	 * Returns the {@link ann.jn.neuroNet.Neuron.INeuronActivationFunction} used to create the
	 * {@link Neuron}s in this net.
	 * @return activation function of neurons
	 */
	public Neuron.INeuronActivationFunction getActivationFunction() {
		return func;
	}
	
	// Private methods
	private void doUpdate() {
		synchronized (updateLock) {
			updating = true;
			
			// Feed the inputs into the input layer
			float[] inputs = new float[net[0].length];
			for (int i = 0; i < net[0].length; i++) {
				inputs[i] = net[0][i].update(new float[]{storedInputs[i]});
			}
			
			// Update the rest of the layers
			for (int layer = 1; layer < net.length; layer++) {
				float[] outputs = new float[net[layer].length];
				
				for (int i = 0; i < net[layer].length; i++) {
					outputs[i] = net[layer][i].update(inputs);
				}
				
				inputs = outputs;
			}
			
			updating = false;
		}
	}
	
	/**
	 * A callback that is accessed when NeuralNet is done updating.
	 */
	public interface INeuralNetCallback {
		/**
		 * Called when NeuralNet is finished updating.
		 */
		public abstract void onFinish();
	}

	private class NeuralNetUpdateThread implements Runnable {
		private INeuralNetCallback cb;

		public NeuralNetUpdateThread(INeuralNetCallback cb) {
			this.cb = cb;

			Thread th = new Thread();
			th.setName("NeuralNet Update thread #" + threads++);
			th.setDaemon(true);

			new Thread(this).start();
		}

		@Override
		public void run() {
			doUpdate();

			if (cb != null) {
				cb.onFinish();
			}
			
			threads--;
		}

	}
}
