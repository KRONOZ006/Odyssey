package net.kronoz.odyssey.cca;

import net.minecraft.util.Identifier;
import org.ladysnake.cca.api.v3.component.Component;

import java.util.Map;

public interface BodyModComponent extends Component {

    Map<String, Identifier> getEquipped();
    void setPart(String slot, Identifier partId);
    void clearSlot(String slot);
    void sync(net.minecraft.entity.player.PlayerEntity player);
    void clientApply(Map<String, Identifier> map);


}
