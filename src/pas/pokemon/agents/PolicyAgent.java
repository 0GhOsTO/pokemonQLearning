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

// JAVA PROJECT IMPORTS
import src.pas.pokemon.senses.CustomSensorArray;

public class PolicyAgent
        extends NeuralQAgent {

    private int epCount = 0;
    private double bestWinRate = -1.0;
    private String bestModelPath = null;
    private int gameCountInCycle = 0;

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
        Sequential qFunction = new Sequential();
        qFunction.add(new Dense(73, 512)); // Larger first layer to capture more patterns
        qFunction.add(new ReLU());
        qFunction.add(new Dense(512, 256));
        qFunction.add(new ReLU());
        qFunction.add(new Dense(256, 128));
        qFunction.add(new ReLU());
        qFunction.add(new Dense(128, 1)); // Q-value output

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
        // Reset counter at start of new cycle
        if (gameCountInCycle >= 170) {
            gameCountInCycle = 0;
        }

        gameCountInCycle++;

        // First 150 games are training (exploration), next 20 are eval (pure
        // exploitation)
        boolean isTraining = (gameCountInCycle <= 150);

        // Epsilon decay tuned for long training runs
        // Start high for broad exploration, decay gradually to maintain some
        // exploration
        double epsilon = 0.0;
        if (isTraining) {
            // Increment episode counter only during training
            epCount++;

            // Decay from 0.6 to 0.1 over first 500k training episodes
            if (epCount < 500000) {
                epsilon = 0.6 - (0.5 * epCount / 500000.0);
            } else {
                epsilon = 0.1;
            }
        }

        if (Math.random() < epsilon) {
            // Explore: random move
            List<MoveView> moves = this.getPotentialMoves(view);
            if (moves != null && !moves.isEmpty()) {
                return moves.get((int) (Math.random() * moves.size()));
            }
        }

        // Exploit
        return this.argmax(view);
    }

    @Override
    public void afterGameEnds(BattleView view) {
        // Episode counter is now incremented in getMove() during training only
        // This ensures epsilon decay is based on actual training episodes, not eval
        // games
    }

}
