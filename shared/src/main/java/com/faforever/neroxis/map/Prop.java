package com.faforever.neroxis.map;

import com.faforever.neroxis.util.vector.Vector2;
import com.faforever.neroxis.util.vector.Vector3;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Data
public class Prop extends PositionedObject {
    private final String path;
    private final float rotation;

    public Prop(String path, Vector2 position, float rotation) {
        this(path, new Vector3(position), rotation);
    }

    public Prop(String path, Vector3 position, float rotation) {
        super(position);
        this.path = path;
        this.rotation = rotation;
    }
}
