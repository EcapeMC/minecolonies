package com.minecolonies.entity.ai.citizen.guard;

import com.minecolonies.colony.buildings.AbstractBuilding;
import com.minecolonies.colony.buildings.AbstractBuildingWorker;
import com.minecolonies.colony.buildings.BuildingGuardTower;
import com.minecolonies.colony.jobs.JobGuard;
import com.minecolonies.colony.permissions.Permissions;
import com.minecolonies.entity.ai.basic.AbstractEntityAISkill;
import com.minecolonies.entity.ai.util.AIState;
import com.minecolonies.entity.ai.util.AITarget;
import com.minecolonies.tileentities.TileEntityColonyBuilding;
import com.minecolonies.util.InventoryFunctions;
import com.minecolonies.util.InventoryUtils;
import com.minecolonies.util.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Random;

import static com.minecolonies.entity.ai.util.AIState.GUARD_RESTOCK;
import static com.minecolonies.entity.ai.util.AIState.IDLE;
import static com.minecolonies.entity.ai.util.AIState.START_WORKING;

/**
 * Abstract class which contains all the guard basics let it be range, melee or magic.
 */
public abstract class AbstractEntityAIGuard extends AbstractEntityAISkill<JobGuard>
{
    /**
     * The start search distance of the guard to track/attack entities may get more depending on the level.
     */
    private static final double MAX_ATTACK_DISTANCE = 20.0D;

    /**
     * Distance the guard starts searching.
     */
    private static final int START_SEARCH_DISTANCE = 5;

    /**
     * Basic delay after operations.
     */
    private static final int BASE_DELAY = 1;

    /**
     * Max amount the guard can shoot arrows before restocking.
     */
    private static final int MAX_ATTACKS = 50;

    /**
     * Y range in which the guard detects other entities.
     */
    private static final double HEIGHT_DETECTION_RANGE = 10D;

    /**
     * Path that close to the patrol target.
     */
    private static final int PATH_CLOSE                = 3;

    /**
     * The distance the guard is searching entities in currently.
     */
    private int currentSearchDistance = START_SEARCH_DISTANCE;

    /**
     * The current target.
     */
    protected EntityLivingBase targetEntity;

    /**
     * Current goTo task.
     */
    private BlockPos currentPatrolTarget;

    /**
     * Containing all close entities.
     */
    private List<Entity> entityList;

    /**
     * Amount of arrows already shot or sword hits dealt.
     */
    protected int attacksExecuted = 0;

    /**
     * Sets up some important skeleton stuff for every ai.
     *
     * @param job the job class
     */
    protected AbstractEntityAIGuard(@NotNull final JobGuard job)
    {
        super(job);
        super.registerTargets(
                new AITarget(IDLE, () -> START_WORKING),
                new AITarget(START_WORKING, () -> GUARD_RESTOCK)
        );
    }


    /**
     * Can be overridden in implementations.
     * <p>
     * Here the AI can check if the armour have to be re rendered and do it.
     */
    @Override
    protected void updateRenderMetaData()
    {
        updateArmor();
    }

    /**
     * Goes back to the building and tries to take armour from it when he hasn't in his inventory.
     * @return the next state to go to.
     */
    protected AIState goToBuilding()
    {
        if(!walkToBuilding())
        {
            final AbstractBuildingWorker workBuilding = getOwnBuilding();
            if(workBuilding != null)
            {
                final TileEntityColonyBuilding chest = workBuilding.getTileEntity();

                for (int i = 0; i < workBuilding.getTileEntity().getSizeInventory(); i++)
                {
                    final ItemStack stack = chest.getStackInSlot(i);

                    if (stack == null)
                    {
                        continue;
                    }

                    if (stack.getItem() instanceof ItemArmor && worker.getInventoryCitizen().getStackInSlot(((ItemArmor) stack.getItem()).armorType) == null)
                    {
                        final int emptySlot = worker.getInventoryCitizen().getFirstEmptySlot();

                        if(emptySlot != -1)
                        {
                            worker.getInventoryCitizen().setInventorySlotContents(emptySlot, stack);
                            chest.setInventorySlotContents(i, null);
                        }
                    }
                    else if(!(stack.getItem() instanceof ItemBow || Utils.doesItemServeAsWeapon(stack)))
                    {
                        //todo dump everything which isn't a weapon or armor
                    }
                }
            }
            attacksExecuted = 0;
            return AIState.GUARD_SEARCH_TARGET;
        }
        return AIState.GUARD_RESTOCK;
    }

    /**
     * Updates the equipment. Always take the first item of each type and set it.
     */
    protected void updateArmor()
    {
        worker.setCurrentItemOrArmor(1, null);
        worker.setCurrentItemOrArmor(2, null);
        worker.setCurrentItemOrArmor(3, null);
        worker.setCurrentItemOrArmor(4, null);


        for(int i = 0; i < worker.getInventoryCitizen().getSizeInventory(); i++)
        {
            ItemStack stack = worker.getInventoryCitizen().getStackInSlot(i);

            if(stack == null || stack.stackSize == 0)
            {
                worker.getInventoryCitizen().setInventorySlotContents(i, null);
                continue;
            }

            if(stack.getItem() instanceof ItemArmor)
            {
                int slotToSetTo = 0;

                switch (((ItemArmor) stack.getItem()).armorType)
                {
                    case 0:
                        slotToSetTo = 3;
                        break;
                    case 1:
                        slotToSetTo = 2;
                        break;
                    case 2:
                        slotToSetTo = 1;
                        break;
                    case 3:
                        slotToSetTo = 0;
                }

                if(worker.getEquipmentInSlot(slotToSetTo + 1) == null)
                {
                    worker.setCurrentItemOrArmor(slotToSetTo + 1, stack);
                }
            }
        }
    }

    /**
     * Chooses a target from the list.
     * @return the next state.
     */
    protected AIState getTarget()
    {
        if(entityList.isEmpty())
        {
            return AIState.GUARD_PATROL;
        }

        if(entityList.get(0) instanceof EntityPlayer)
        {
            if(worker.getColony() != null && worker.getColony().getPermissions().hasPermission((EntityPlayer) entityList.get(0), Permissions.Action.GUARDS_ATTACK))
            {
                targetEntity = (EntityLivingBase) entityList.get(0);
                worker.getNavigator().clearPathEntity();
                return AIState.GUARD_HUNT_DOWN_TARGET;
            }
            entityList.remove(0);
            setDelay(BASE_DELAY);
            return AIState.GUARD_GET_TARGET;
        }
        else if (!worker.getEntitySenses().canSee(entityList.get(0)) || !(entityList.get(0)).isEntityAlive())
        {
            entityList.remove(0);
            setDelay(BASE_DELAY);
            return AIState.GUARD_GET_TARGET;
        }
        else
        {
            worker.getNavigator().clearPathEntity();
            targetEntity = (EntityLivingBase) entityList.get(0);
            return AIState.GUARD_HUNT_DOWN_TARGET;
        }
    }

    /**
     * Searches for the next taget.
     * @return the next AIState.
     */
    protected AIState searchTarget()
    {
        entityList = this.worker.worldObj.getEntitiesWithinAABB(EntityMob.class, this.getTargetableArea(currentSearchDistance));
        entityList.addAll(this.worker.worldObj.getEntitiesWithinAABB(EntitySlime.class, this.getTargetableArea(currentSearchDistance)));
        entityList.addAll(this.worker.worldObj.getEntitiesWithinAABB(EntityPlayer.class, this.getTargetableArea(currentSearchDistance)));

        setDelay(BASE_DELAY);
        if(entityList.isEmpty())
        {
            if(currentSearchDistance < getMaxVision())
            {
                currentSearchDistance += START_SEARCH_DISTANCE;
            }
            else
            {
                currentSearchDistance = START_SEARCH_DISTANCE;
                return AIState.GUARD_PATROL;
            }

            return AIState.GUARD_SEARCH_TARGET;
        }
        return AIState.GUARD_GET_TARGET;
    }

    /**
     * Getter for the vision or attack distance.
     * @return the max vision.
     */
    private double getMaxVision()
    {
        BuildingGuardTower guardTower = (BuildingGuardTower) worker.getWorkBuilding();
        return (guardTower == null) ? 0 : (MAX_ATTACK_DISTANCE + guardTower.getBonusVision());
    }

    /**
     * Getter calculating how many arrows the guard may shoot or deal sword hits until restock.
     * @return the amount.
     */
    protected int getMaxAttacksUntilRestock()
    {
        BuildingGuardTower guardTower = (BuildingGuardTower) worker.getWorkBuilding();
        return (guardTower == null) ? 0 : (MAX_ATTACKS + guardTower.getBuildingLevel());
    }

    /**
     * Lets the guard patrol inside the colony area searching for mobs.
     * @return the next state to go.
     */
    protected AIState patrol()
    {
        worker.setAIMoveSpeed(1);

        if(currentPatrolTarget == null)
        {
            currentPatrolTarget = getRandomBuilding();
        }

        if(worker.isWorkerAtSiteWithMove(currentPatrolTarget, PATH_CLOSE))
        {
            currentPatrolTarget = null;
        }

        return AIState.GUARD_SEARCH_TARGET;
    }

    /**
     * Gets a random building from his colony.
     * @return a random blockPos.
     */
    private BlockPos getRandomBuilding()
    {
        if(worker.getColony() == null || getOwnBuilding() == null)
        {
            return worker.getPosition();
        }

        Collection<AbstractBuilding> buildingList =  worker.getColony().getBuildings().values();

        Object[] buildingArray = buildingList.toArray();

        int random = new Random().nextInt(buildingArray.length);

        AbstractBuilding building = (AbstractBuilding) buildingArray[random];
        if(building instanceof BuildingGuardTower)
        {
            return this.getOwnBuilding().getLocation();
        }

        return building.getLocation();
    }

    private AxisAlignedBB getTargetableArea(double range)
    {
        return this.worker.getEntityBoundingBox().expand(range, HEIGHT_DETECTION_RANGE, range);
    }
}