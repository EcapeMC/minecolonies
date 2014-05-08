package com.minecolonies.blocks;

import com.minecolonies.MineColonies;
import com.minecolonies.lib.Constants;
import com.minecolonies.tileentities.TileEntityBuildable;
import com.minecolonies.tileentities.TileEntityTownHall;
import com.minecolonies.util.CreativeTab;
import com.minecolonies.util.IColony;
import com.minecolonies.util.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.UUID;

public abstract class BlockInformator extends Block implements IColony, ITileEntityProvider
{
    protected int workingRange;

    private IIcon[] icons = new IIcon[6];// 0 = top, 1 = bot, 2-5 = sides;

    public BlockInformator(Material material)
    {
        super(material);
        setCreativeTab(CreativeTab.mineColoniesTab);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer entityPlayer, int par6, float par7, float par8, float par9)
    {
        entityPlayer.openGui(MineColonies.instance, 0, world, x, y, z);
        return true;
    }

    @Override
    public void registerBlockIcons(IIconRegister iconRegister)
    {
        icons[0] = iconRegister.registerIcon(Constants.MODID.toLowerCase() + ":" + getName() + "top");
        icons[1] = icons [0];
        for(int i = 2; i <= 5; i++)
        {
            icons[i] = iconRegister.registerIcon(Constants.MODID.toLowerCase() + ":" + "sideChest");
        }
    }

    @Override
    public IIcon getIcon(int side, int meta)
    {
        return icons[side];
    }

    /**
     * Attempts to add citizen to a working hut
     * @param tileEntityTownHall TileEntityTownHall bound to
     * @param world world
     * @param x xcoord
     * @param y ycoord
     * @param z zcoord
     */
    public void attemptToAddIdleCitizens(TileEntityTownHall tileEntityTownHall, World world, int x, int y, int z)
    {
        TileEntity tileEntity = world.getTileEntity(x,y,z);
        if(!(tileEntity instanceof TileEntityBuildable)) return;
        ArrayList<UUID> citizens = tileEntityTownHall.getCitizens();
        //TODO ATTEMPT TO ADD
    }

    /**
     * Sets the TE's townhall to the closest townhall
     *
     * @param world world
     * @param x xcoord
     * @param y ycoord
     * @param z zcoord
     */
    public void addClosestTownhall(World world, int x, int y, int z)
    {
        TileEntityTownHall tileEntityTownHall = Utils.getClosestTownHall(world, x, y, z);
        if(tileEntityTownHall != null)
        {
            if(world.getTileEntity(x,y,z) instanceof TileEntityBuildable)
            {
                TileEntityBuildable tileEntityBuildable = (TileEntityBuildable) world.getTileEntity(x,y,z);
                tileEntityBuildable.setTownHall(tileEntityTownHall); //TODO, check for owner first
                attemptToAddIdleCitizens(tileEntityTownHall, world, x, y, z);
            }
        }
    }


    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entityLivingBase, ItemStack itemStack)
    {
        if (world.isRemote) return;

        if(Utils.getDistanceToClosestTownHall(world, x, y, z) < Constants.MAXDISTANCETOTOWNHALL)
        {
            addClosestTownhall(world, x, y, z);
        }
    }
}