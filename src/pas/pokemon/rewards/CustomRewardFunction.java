package src.pas.pokemon.rewards;

// SYSTEM IMPORTS

// JAVA PROJECT IMPORTS
import edu.bu.pas.pokemon.agents.rewards.RewardFunction;
import edu.bu.pas.pokemon.agents.rewards.RewardFunction.RewardType;
import edu.bu.pas.pokemon.core.Pokemon;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.core.Team.TeamView;

public class CustomRewardFunction
        extends RewardFunction {

    public CustomRewardFunction() {
        // Defining as R(s, a, s')
        super(RewardType.STATE_ACTION_STATE); // currently configured to produce rewards as a function of the state
    }

    public double getLowerBound() {
        // TODO: change this. Reward values must be finite!
        return -3000.0;
    }

    public double getUpperBound() {
        // TODO: change this. Reward values must be finite!
        return 3000.0;
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

    public double getStateActionStateReward(final BattleView state,
            final MoveView action,
            final BattleView nextState) {
        double reward = 0.0;
        // Treating team 2 as the oponent.
        TeamView myTeam = state.getTeam1View();
        TeamView oppTeam = state.getTeam2View();
        TeamView myNxtTeam = nextState.getTeam1View();
        TeamView oppNxtTeam = nextState.getTeam2View();

        // Get the active pokemon for the both team.
        PokemonView myPokemon = myTeam.getActivePokemonView();
        PokemonView oppPokemon = oppTeam.getActivePokemonView();
        PokemonView myNxtPokemon = myNxtTeam.getActivePokemonView();
        PokemonView oppNxtPokemon = oppNxtTeam.getActivePokemonView();

        // If the battle is over ...
        if (nextState.isOver()) {
            if (teamDead(oppNxtTeam)) {
                reward += 1000.0; // Win
            } else if (teamDead(myNxtTeam)) {
                reward -= 1000.0; // Lose
            }
            // Game is over
            return reward;
        }

        // Pokemon dead.
        if (oppPokemon != null && oppNxtPokemon != null) {
            if (!oppNxtPokemon.hasFainted() && oppPokemon.hasFainted()) {
                reward += 500.0; // Opponent pokemon dead.
            }
        }
        if (myPokemon != null && myNxtPokemon != null) {
            if (!myNxtPokemon.hasFainted() && myPokemon.hasFainted()) {
                reward -= 500.0; // My pokemon dead.
            }
        }

        // Pokemon damaging.
        if (oppPokemon != null && oppNxtPokemon != null) {
            double oppHpDiff = oppPokemon.getCurrentStat(Stat.HP) - oppNxtPokemon.getCurrentStat(Stat.HP);
            reward += oppHpDiff; // Opponent getting damaged.
        }
        if (myPokemon != null && myNxtPokemon != null) {
            double myHpDiff = myPokemon.getCurrentStat(Stat.HP) - myNxtPokemon.getCurrentStat(Stat.HP);
            reward -= myHpDiff; // My pokemon getting damaged.
        }

        // Condition based rewarding.
        if (oppPokemon != null && oppNxtPokemon != null) {
            String oppStatus = oppPokemon.getNonVolatileStatus().toString();
            String oppNxtStatus = oppNxtPokemon.getNonVolatileStatus().toString();

            if (oppStatus.equals("NONE") && !oppNxtStatus.equals("NONE")) {
                reward += 50.0; // Opponent getting status condition.
            }
        }

        if (myPokemon != null && myNxtPokemon != null) {
            String myStatus = myPokemon.getNonVolatileStatus().toString();
            String myNxtStatus = myNxtPokemon.getNonVolatileStatus().toString();

            if (myStatus.equals("NONE") && !myNxtStatus.equals("NONE")) {
                reward -= 50.0; // My pokemon getting status condition.
            }
        }

        int myTeamTotHP = getTeamTotHP(myNxtTeam);
        int oppTeamTotHP = getTeamTotHP(oppNxtTeam);
        int prevMyTeamTotHP = getTeamTotHP(myTeam);
        int prevOppTeamTotHP = getTeamTotHP(oppTeam);

        int hpDiff = (prevMyTeamTotHP - myTeamTotHP) - (prevOppTeamTotHP - oppTeamTotHP);
        reward += hpDiff / 50.0; // Team HP difference, we reward.

        return reward;
    }

    // Total team hp.
    private int getTeamTotHP(TeamView team) {
        int totHP = 0;
        for (int i = 0; i < team.size(); i++) {
            PokemonView pkmn = team.getPokemonView(i);
            if (pkmn != null && !pkmn.hasFainted()) {
                totHP += pkmn.getCurrentStat(Stat.HP);
            }
        }
        return totHP;
    }

    // Whole team is dead.
    private boolean teamDead(TeamView team) {
        for (int i = 0; i < team.size(); i++) {
            PokemonView pkmn = team.getPokemonView(i);
            if (pkmn != null && !pkmn.hasFainted()) {
                return false;
            }
        }
        return true;
    }

}
