package src.pas.pokemon.agents;

// SYSTEM IMPORTS
import net.sourceforge.argparse4j.inf.Namespace;
import java.util.List;

import edu.bu.pas.pokemon.agents.NeuralQAgent;
import edu.bu.pas.pokemon.agents.senses.SensorArray;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.enums.Type;
import edu.bu.pas.pokemon.linalg.Matrix;
import edu.bu.pas.pokemon.nn.Model;
import edu.bu.pas.pokemon.nn.models.Sequential;
import edu.bu.pas.pokemon.nn.layers.Dense;
import edu.bu.pas.pokemon.nn.layers.ReLU;
import edu.bu.pas.pokemon.nn.layers.Tanh;
import edu.bu.pas.pokemon.nn.layers.Sigmoid;

// JAVA PROJECT IMPORTS
import src.pas.pokemon.senses.CustomSensorArray;

public class PolicyAgent
        extends NeuralQAgent {

    private int epCount = 0;

    public PolicyAgent() {
        super();
    }

    public void initializeSenses(Namespace args) {
        SensorArray modelSenses = new CustomSensorArray();

        this.setSensorArray(modelSenses);
    }

    @Override
    public void initialize(Namespace args) {
        // make sure you call this, this will call your initModel() and set a field
        // AND if the command line argument "inFile" is present will attempt to set
        // your model with the contents of that file.
        super.initialize(args);

        // what senses will your neural network have?
        this.initializeSenses(args);

        // do what you want just don't expect custom command line options to be
        // available
        // when I'm testing your code
    }

    @Override
    public Model initModel() {
        // TODO: create your neural network

        // currently this creates a one-hidden-layer network
        Sequential qFunction = new Sequential();
        qFunction.add(new Dense(75, 256));
        qFunction.add(new Tanh());
        qFunction.add(new Dense(256, 128));
        qFunction.add(new ReLU());
        qFunction.add(new Dense(128, 1)); // ouput

        return qFunction;
    }

    @Override
    public Integer chooseNextPokemon(BattleView view) {
        // TODO: change this to something more intelligent!

        // find a pokemon that is alive
        PokemonView oppPkmn = this.getOpponentTeamView(view).getActivePokemonView();
        if (oppPkmn == null) {
            for (int i = 0; i < this.getMyTeamView(view).size(); i++) {
                if (!this.getMyTeamView(view).getPokemonView(i).hasFainted()) {
                    return i;
                }
            }
            return null;
        }

        int best = -1;
        double bestAdv = -999.0;

        for (int i = 0; i < this.getMyTeamView(view).size(); i++) {
            PokemonView pkmn = this.getMyTeamView(view).getPokemonView(i);
            if (!pkmn.hasFainted()) {
                double adv = calcType(pkmn, oppPkmn);
                if (adv > bestAdv) {
                    bestAdv = adv;
                    best = i;
                }
            }
        }

        return best;
    }

    private double calcType(PokemonView myPkmn, PokemonView oppPkmn) {
        double adv = 0.0;

        Type myType1 = myPkmn.getCurrentType1();
        Type myType2 = myPkmn.getCurrentType2();
        Type oppType1 = oppPkmn.getCurrentType1();
        Type oppType2 = oppPkmn.getCurrentType2();

        // Calculate offensive advantage
        if (myType1 != null && oppType1 != null) {
            adv += Type.getEffectivenessModifier(myType1, oppType1);
        }
        if (myType1 != null && oppType2 != null) {
            adv += Type.getEffectivenessModifier(myType1, oppType2);
        }
        if (myType2 != null && oppType1 != null) {
            adv += Type.getEffectivenessModifier(myType2, oppType1);
        }
        if (myType2 != null && oppType2 != null) {
            adv += Type.getEffectivenessModifier(myType2, oppType2);
        }

        // Calculate defensive advantage
        if (oppType1 != null && myType1 != null) {
            adv -= Type.getEffectivenessModifier(oppType1, myType1);
        }
        if (oppType1 != null && myType2 != null) {
            adv -= Type.getEffectivenessModifier(oppType1, myType2);
        }
        if (oppType2 != null && myType1 != null) {
            adv -= Type.getEffectivenessModifier(oppType2, myType1);
        }
        if (oppType2 != null && myType2 != null) {
            adv -= Type.getEffectivenessModifier(oppType2, myType2);
        }

        return adv;
    }

    @Override
    public MoveView getMove(BattleView view) {
        // TODO: change this to include random exploration during training and maybe use
        // the transition model to make
        // good predictions?
        // if you choose to use the transition model you might want to also override the
        // makeGroundTruth(...) method
        // to not use temporal difference learning

        // currently always tries to argmax the learned model
        // this is not a good idea to always do when training. When playing evaluation
        // games you *do* want to always
        // argmax your model, but when training our model may not know anything yet! So,
        // its a good idea to sometime
        // during training choose *not* to argmax the model and instead choose something
        // new at random.

        // HOW that randomness works and how often you do it are up to you, but it
        // *will* affect the quality of your
        // learned model whether you do it or not!
        // Epsilon-greedy exploration: decay from 0.5 to 0.05 over 40k episodes
        double epsilon = (epCount < 40000) ? 0.5 - (0.45 * epCount / 40000.0) : 0.05;
        if (Math.random() < epsilon) {
            // Explore: random move
            List<MoveView> moves = this.getPotentialMoves(view);
            if (moves != null && !moves.isEmpty()) {
                return moves.get((int) (Math.random() * moves.size()));
            }
            // argmax
        }

        // Exploit
        return this.argmax(view);
    }

    @Override
    public void afterGameEnds(BattleView view) {
        epCount++;
    }

}
