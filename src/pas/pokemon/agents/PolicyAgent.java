package src.pas.pokemon.agents;

// SYSTEM IMPORTS
import net.sourceforge.argparse4j.inf.Namespace;
import java.util.List;

// JAVA PROJECT IMPORTS
import edu.bu.pas.pokemon.agents.NeuralQAgent;
import edu.bu.pas.pokemon.core.Team;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.enums.Type;
import edu.bu.pas.pokemon.nn.Model;
import edu.bu.pas.pokemon.nn.models.Sequential;
import edu.bu.pas.pokemon.nn.layers.Dense;
import edu.bu.pas.pokemon.nn.layers.ReLU;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.enums.Stat;
import src.pas.pokemon.senses.CustomSensorArray;

public class PolicyAgent extends NeuralQAgent {

    // Epsilon is decaying from the 0.8
    private double eps = 0.8; // Will be decreasing in every games. 
    private final double epsDecay = 0.997; // Decaying by the factor of 0.997
    private final double minEps = 0.05; // Minimum epsilon(At list have some exploration)

    // Training mode flag
    private boolean isTraining = true;

    // Our custom sensor array
    private CustomSensorArray sensorArray = null;

    public PolicyAgent() {
        super();
    }

    public void initializeSenses(Namespace args) {
        this.sensorArray = new CustomSensorArray();
        this.setSensorArray(this.sensorArray);
    }

    @Override
    public void initialize(Namespace args) {
        // First call parent initialize
        super.initialize(args);
        // Initialize our custom senses. 
        this.initializeSenses(args);
    }

    @Override
    public Model initModel() {
        // 65 input features including bias
        // Network: 65 -> 64 -> 1
        Sequential qFunction = new Sequential();
        qFunction.add(new Dense(65, 64));
        qFunction.add(new ReLU()); // ReLu is much better compare to Tahn
        qFunction.add(new Dense(64, 1));

        return qFunction;
    }

    @Override
    public Integer chooseNextPokemon(BattleView view) {

        TeamView myTeam = this.getMyTeamView(view);
        TeamView oppTeam;
        // Grab the team index and view of them. 
        if (myTeam.getBattleIdx() == 0) {
            oppTeam = view.getTeam2View();
        } else {
            oppTeam = view.getTeam1View();
        }

        // Opponent's active Pokemon
        PokemonView oppPkmn = oppTeam.getActivePokemonView();

        // bug catching
        if (oppPkmn == null) {
            for (int i = 0; i < this.getMyTeamView(view).size(); i++) {
                if (!this.getMyTeamView(view).getPokemonView(i).hasFainted()) {
                    return i;
                }
            }
            return null; // Should basically never happen
        }

        int best = -1;
        double bestAdv = -999.0;

        // Team switching.
        for (int i = 0; i < myTeam.size(); i++) {
            PokemonView pkmn = myTeam.getPokemonView(i);
            if (pkmn.hasFainted() || i == myTeam.getActivePokemonIdx()) {
                continue; // Skip fainted and active Pokemon
            } else {
                double adv = calcType(pkmn, oppPkmn);
                // Look at the HP level as well. Do not want to use the fainted pokemon. 
                double hpLevel = (double) pkmn.getCurrentStat(Stat.HP) / (double) pkmn.getInitialStat(Stat.HP);
                double res = 0.4 * adv + 0.6 * hpLevel; // 40% type advantage, 60% HP level
                if (res > bestAdv) {
                    bestAdv = res;
                    best = i;
                }
            }
        }

        // Return the best one or just something that is alive.
        if (best >= 0) {
            return best;
        } else {
            // If unable to determine best, return the random first pokemon that is alive. 
            for (int i = 0; i < myTeam.size(); i++) {
                if (myTeam.getPokemonView(i) != null && !myTeam.getPokemonView(i).hasFainted()) {
                    return i;
                }
            }
        }

        // Fallback: if somehow nothing is alive
        return best;
    }

    // Calculate type advantage from myPkmn to oppPkmn
    private double calcType(PokemonView myPkmn, PokemonView oppPkmn) {
        Type myType1 = myPkmn.getCurrentType1();
        Type myType2 = myPkmn.getCurrentType2();
        Type oppType1 = oppPkmn.getCurrentType1();
        Type oppType2 = oppPkmn.getCurrentType2();

        // Use the actual game's calculation for type advantage. 
        double offMult1 = 1.0; // Effectiveness of myType1
        if (myType1 != null) {
            if (oppType1 != null) {
                offMult1 *= Type.getEffectivenessModifier(myType1, oppType1);
            }
            if (oppType2 != null) {
                offMult1 *= Type.getEffectivenessModifier(myType1, oppType2);
            }
        }

        double offMult2 = 1.0; // Effectiveness of myType2
        if (myType2 != null) {
            if (oppType1 != null) {
                offMult2 *= Type.getEffectivenessModifier(myType2, oppType1);
            }
            if (oppType2 != null) {
                offMult2 *= Type.getEffectivenessModifier(myType2, oppType2);
            }
        }

        // Use best offensive multiplier. 
        double offensiveAdv = Math.max(offMult1, offMult2) - 1.0;

        // Defensive disadvantage
        double defMult1 = 1.0; // How well oppType1 hits me
        if (oppType1 != null) {
            if (myType1 != null) {
                defMult1 *= Type.getEffectivenessModifier(oppType1, myType1);
            }
            if (myType2 != null) {
                defMult1 *= Type.getEffectivenessModifier(oppType1, myType2);
            }
        }

        double defMult2 = 1.0; // How well oppType2 hits me
        if (oppType2 != null) {
            if (myType1 != null) {
                defMult2 *= Type.getEffectivenessModifier(oppType2, myType1);
            }
            if (myType2 != null) {
                defMult2 *= Type.getEffectivenessModifier(oppType2, myType2);
            }
        }

        // Use worst defensive multiplier 
        double defensiveDisadv = Math.max(defMult1, defMult2) - 1.0;

        // Good offensive advantage and low defensive disadvantage is the best. 
        return offensiveAdv - defensiveDisadv;
    }

    @Override
    public MoveView getMove(BattleView view) {
        TeamView myTeam = this.getMyTeamView(view);
        PokemonView myActPkmn = myTeam.getActivePokemonView();

        if (myActPkmn == null) {
            return this.argmax(view);
        }

        // Grab the available Moves. 
        List<MoveView> legalMoves = myActPkmn.getAvailableMoves();
        if (legalMoves == null || legalMoves.isEmpty()) {
            return this.argmax(view);
        }

        // During the training, do epsilon-greedy action seletion. 
        if (view.getRandom().nextDouble() < this.eps) {
            int idx = view.getRandom().nextInt(legalMoves.size());
            return legalMoves.get(idx);
        }

        // argmax. 
        return this.argmax(view);
    }

    @Override
    public void afterGameEnds(BattleView view) {
        // After the game is over, decay the epsilon
        if (this.isTraining) {
            this.eps = Math.max(this.eps * this.epsDecay, this.minEps);
        }
    }

    @Override
    // Called when the training mode start. 
    public void train() {
        this.isTraining = true;
        super.train();
    }

    // Called when the evaluation mdoe started. 
    @Override
    public void eval() {
        this.isTraining = false;
        super.eval();
    }
}
