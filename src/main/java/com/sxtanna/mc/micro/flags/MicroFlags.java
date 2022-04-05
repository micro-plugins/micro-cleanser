package com.sxtanna.mc.micro.flags;

import org.jetbrains.annotations.Nullable;

import org.bukkit.Material;

import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.IntegerFlag;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import com.sk89q.worldguard.protection.flags.SetFlag;

public enum MicroFlags {
    ;

    public static final IntegerFlag       CLEANSER_DELAY = new IntegerFlag("cleanser-delay");
    public static final SetFlag<Material> CLEANSER_TYPES = new SetFlag<>("cleanser-types", new BlockMaterialFlag(null));


    private static final class BlockMaterialFlag extends Flag<Material> {

        public BlockMaterialFlag(String name) {
            super(name);
        }

        @Override
        public Material parseInput(FlagContext context) throws InvalidFlagFormat {
            final var type = Material.matchMaterial(context.getUserInput());

            if (type == null) {
                throw new InvalidFlagFormat("Invalid material name!");
            }

            if (!type.isBlock()) {
                throw new InvalidFlagFormat("Defined material must be a placeable block!");
            }

            return type;
        }

        @Override
        public Material unmarshal(@Nullable Object o) {
            return Material.matchMaterial(String.valueOf(o));
        }

        @Override
        public Object marshal(Material o) {
            return o.name();
        }

    }

}
