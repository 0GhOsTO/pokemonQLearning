package src.pas.pokemon.rewards;

// SYSTEM IMPORTS

// JAVA PROJECT IMPORTS
import edu.bu.pas.pokemon.agents.rewards.RewardFunction;
import edu.bu.pas.pokemon.agents.rewards.RewardFunction.RewardType;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.enums.NonVolatileStatus;
import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.core.Team.TeamView;

public class CustomRewardFunction
        extends RewardFunction {

    public CustomRewardFunction() {
        // Defining as R(s, a, s')
        super(RewardType.STATE_ACTION_STATE); // currently configured to produce rewards as a function of the state
    }

    public double getLowerBound() {
        // Reward val
        return -200.0;
    }

    public double getUpperBound() {
        // Reward val
        return 200.0;
    }

    // NOT USED
    public double getStateReward(final BattleView state) {
        return 0d;
    }

    // NOT USED
    public double getStateActionReward(final BattleView state,
            final MoveView action) {
        return 0d;
    }

    //Decrease the amount of reward since we have way too many precise rewards causing instability.
    public double getStateActionStateReward(final BattleView state,
            final MoveView action,
            final BattleView nextState) {
        double reward = 0.0;

        // grab the team index. 
        int myTeamIdx = this.getTeamIdx();
        TeamView myTeam = (myTeamIdx == 0) ? state.getTeam1View() : state.getTeam2View();
        TeamView oppTeam = (myTeamIdx == 0) ? state.getTeam2View() : state.getTeam1View();
        TeamView myNxtTeam = (myTeamIdx == 0) ? nextState.getTeam1View() : nextState.getTeam2View();
        TeamView oppNxtTeam = (myTeamIdx == 0) ? nextState.getTeam2View() : nextState.getTeam1View();

        // Win/Los detection. 
        if (nextState.isOver()) {
            if (teamDead(oppNxtTeam)) {
                reward += 100.0; // Win
            } else if (teamDead(myNxtTeam)) {
                reward -= 100.0; // Lose
            }
            return Math.max(getLowerBound(), Math.min(getUpperBound(), reward));
        }

        // HP Advantage Reward.
        double myTeamHpFrac = getTeamHpPercent(myTeam);
        double oppTeamHpFrac = getTeamHpPercent(oppTeam);
        double myTeamNextHpFrac = getTeamHpPercent(myNxtTeam);
        double oppTeamNextHpFrac = getTeamHpPercent(oppNxtTeam);
        
        double hpAdvantageOld = myTeamHpFrac - oppTeamHpFrac;
        double hpAdvantageNew = myTeamNextHpFrac - oppTeamNextHpFrac;
        reward += 30.0 * (hpAdvantageNew - hpAdvantageOld);

        /* Not using these. WAY TOO MANY PRECISED REWARDS CAUSING INSTABILITY.
        // Get the active pokemon for both teams.
        PokemonView myPokemon = myTeam.getActivePokemonView();
        PokemonView oppPokemon = oppTeam.getActivePokemonView();
        PokemonView myNxtPokemon = myNxtTeam.getActivePokemonView();
        PokemonView oppNxtPokemon = oppNxtTeam.getActivePokemonView();
        // Pokemon dead.
        if (oppPokemon != null && oppNxtPokemon != null) {
            if (!oppPokemon.hasFainted() && oppNxtPokemon.hasFainted()) {
                reward += 8.0;
            }
        }
        if (myPokemon != null && myNxtPokemon != null) {
            if (!myPokemon.hasFainted() && myNxtPokemon.hasFainted()) {
                reward -= 8.0;
            }
        }

        // Pokemon damaging.
        if (oppPokemon != null && oppNxtPokemon != null) {
            double oppHpDiff = oppPokemon.getCurrentStat(Stat.HP) - oppNxtPokemon.getCurrentStat(Stat.HP);
            reward += oppHpDiff * 0.02;
        }
        if (myPokemon != null && myNxtPokemon != null) {
            double myHpDiff = myPokemon.getCurrentStat(Stat.HP) - myNxtPokemon.getCurrentStat(Stat.HP);
            reward -= myHpDiff * 0.02;

        // Condition based rewarding.
        if (oppPokemon != null && oppNxtPokemon != null) {
            NonVolatileStatus oppStatus = oppPokemon.getNonVolatileStatus();
            NonVolatileStatus oppNxtStatus = oppNxtPokemon.getNonVolatileStatus();
            if (oppStatus == NonVolatileStatus.NONE && oppNxtStatus != NonVolatileStatus.NONE) {
                reward += getStatusReward(oppNxtStatus);
            }
        }

        if (myPokemon != null && myNxtPokemon != null) {
            NonVolatileStatus myStatus = myPokemon.getNonVolatileStatus();
            NonVolatileStatus myNxtStatus = myNxtPokemon.getNonVolatileStatus();

            if (myStatus == NonVolatileStatus.NONE && myNxtStatus != NonVolatileStatus.NONE) {
                reward -= getStatusReward(myNxtStatus); // My pokemon getting status condition.
            }
        }

        reward += calcBoostReward(myPokemon, myNxtPokemon, true);
        reward += calcBoostReward(oppPokemon, oppNxtPokemon, false);

        // Team-level game progress rewards
        reward += calcTeamProgressReward(myTeam, myNxtTeam, oppTeam, oppNxtTeam);

        // Coverage and type effectiveness rewards
        if (action != null && action.getType() != null && oppPokemon != null) {
            double effectiveness = calcMoveEffectiveness(action, oppPokemon);

            // Reward super effective coverage
            if (effectiveness >= 2.0) {
                reward += 1.5; // Strong coverage hit
            }
            // Penalty for not very effective hits. 
            else if (effectiveness > 0.0 && effectiveness < 1.0) {
                reward -= 1.0; // Resisted hit penalty
            }
            // Strong penalty for immunity
            else if (effectiveness == 0.0 && action.getPower() != null && action.getPower() > 0) {
                reward -= 3.0; // Critical mistake: attacking immunity
            }
        }

        if (action != null && action.getPower() != null) {
            Integer moveP = action.getPower();
            // Penalty for the bad move.
            if (moveP < 40 && moveP > 0) {
                reward -= 0.5; // Penalize low power moves (scaled for 6 Pokemon)
            }
        }

        if (oppPokemon != null && oppNxtPokemon != null) {
            if (!oppPokemon.hasFainted() && oppPokemon != oppNxtPokemon) {
                reward += 2.5; // Forcing the switch to happen. 
            }
        }

        // Pivot move momentum rewards
        if (action != null && action.getName() != null) {
            String moveName = action.getName().toLowerCase();
            if (moveName.contains("u-turn") || moveName.contains("volt switch") || moveName.contains("flip turn")) {
                // Reward maintaining momentum with pivot moves according to the strategy
                reward += 1.5;
            }
        }

        reward -= 0.1; 
        */

        // Clamp final reward to the bounds. 
        reward = Math.max(getLowerBound(), Math.min(getUpperBound(), reward));
        return reward;
    }

    /* Status reward
    private double getStatusReward(NonVolatileStatus status) {
        switch (status) {
            case TOXIC:
                return 2.5; // continuous escalating damage
            case BURN:
                return 2.0; // damage + attack reduction
            case PARALYSIS:
                return 1.8; // speed reduction 
            case POISON:
                return 1.5; // continuous damage 
            case SLEEP:
                return 2.0; // pokemon can't move for multiple turns 
            case FREEZE:
                return 2.0; // similar to sleep 
            default:
                return 0.0;
        }
    }
    */

    /* boosting reward following the strategy of prioritizing offensive and speed boosts
    private double calcBoostReward(PokemonView before, PokemonView after, boolean isMine) {
        if (before == null || after == null) {
            return 0.0;
        }
        double boost = 0.0;

        Stat[] checkStats = {
                Stat.ATK,
                Stat.DEF,
                Stat.SPD,
                Stat.SPATK,
                Stat.SPDEF,
                Stat.ACC,
                Stat.EVASIVE
        };

        for (Stat stat : checkStats) {
            int beforeBoost = before.getStatMultiplier(stat);
            int afterBoost = after.getStatMultiplier(stat);
            int diff = afterBoost - beforeBoost;
            if (diff != 0) {
                double multiplier = 1.0;
                if (stat == Stat.ATK || stat == Stat.SPATK || stat == Stat.SPD) {
                    multiplier = 1.5; // Prioritize offensive/speed stats for sweepers.
                }
                if (isMine) {
                    boost += diff * 0.5 * multiplier; // Reward my boosts 
                } else {
                    boost -= diff * 0.5 * multiplier; // Penalize opponent's boosts 
                }
            }
        }
        return boost;
    }
    */

    // Grab the team's HP percentage. 
    private double getTeamHpPercent(TeamView team) {
        double totalCurrentHp = 0.0;
        double totalMaxHp = 0.0;

        for (int i = 0; i < team.size(); i++) {
            PokemonView pkmn = team.getPokemonView(i);
            if (pkmn != null) {
                totalCurrentHp += pkmn.getCurrentStat(Stat.HP);
                totalMaxHp += pkmn.getInitialStat(Stat.HP);
            }
        }

        return totalMaxHp > 0 ? totalCurrentHp / totalMaxHp : 0.0;
    }

    /* # of fainted. 
    private int getTeamFaintedCount(TeamView team) {
        int faintedCount = 0;
        for (int i = 0; i < team.size(); i++) {
            PokemonView pkmn = team.getPokemonView(i);
            if (pkmn != null && pkmn.hasFainted()) {
                faintedCount++;
            }
        }
        return faintedCount;
    }
    */

    // Check if the team is dead. 
    private boolean teamDead(TeamView team) {
        for (int i = 0; i < team.size(); i++) {
            PokemonView pkmn = team.getPokemonView(i);
            if (pkmn != null && !pkmn.hasFainted()) {
                return false;
            }
        }
        return true;
    }

    /* Check the effectiveness of a move against a defender. 
    private double calcMoveEffectiveness(MoveView move, PokemonView defender) {
        if (move == null || move.getType() == null || defender == null) {
            return 1.0;
        }

        edu.bu.pas.pokemon.core.enums.Type moveType = move.getType();
        edu.bu.pas.pokemon.core.enums.Type defType1 = defender.getCurrentType1();
        edu.bu.pas.pokemon.core.enums.Type defType2 = defender.getCurrentType2();

        double effect1 = 1.0;
        double effect2 = 1.0;

        if (defType1 != null) {
            effect1 = edu.bu.pas.pokemon.core.enums.Type.getEffectivenessModifier(moveType, defType1);
        }
        if (defType2 != null) {
            effect2 = edu.bu.pas.pokemon.core.enums.Type.getEffectivenessModifier(moveType, defType2);
        }

        return effect1 * effect2;
    }
    */

}