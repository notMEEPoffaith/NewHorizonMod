package newhorizon.func;

import arc.Core;
import arc.audio.Sound;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.input.KeyCode;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.actions.Actions;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.TextArea;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.core.UI;
import mindustry.core.World;
import mindustry.entities.bullet.BulletType;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.ui.Cicon;
import mindustry.ui.Fonts;
import mindustry.ui.Links;
import mindustry.ui.Styles;
import mindustry.world.modules.ItemModule;
import newhorizon.vars.NHVars;

import java.lang.reflect.Field;
import java.text.DecimalFormat;

import static mindustry.Vars.*;

public class TableFs{
    private static final int tableZ = 2;
    private static final DecimalFormat df = new DecimalFormat("######0.00");
    private static String sx = "", sy = "";
    private static final TextArea xArea = new TextArea(""), yArea = new TextArea("");
    private static boolean autoMove, onBoost, floatTable, isInner;
    private static final Vec2 point = new Vec2(-1, -1);
    private static int spawnNum = 1;
    private static Team selectTeam = Team.sharded;
    private static UnitType selected = UnitTypes.alpha;
    private static long lastToast;
    
    private static void setStr(){
        sx = sy = "";
    }
    private static void setText(){
        xArea.setText(sx);
        yArea.setText(sy);
    }
    private static boolean pointValid(){
        return point.x >= 0 && point.y >= 0 && point.x <= world.width() * tilesize && point.y <= world.height() * tilesize;
    }
    
    private static class Inner extends Table{
        Inner(){
            background(Tex.button);
            isInner = true;
            setSize(LEN * 12f, (LEN + OFFSET) * 3);
            button(Icon.cancel, Styles.clearTransi, () -> {
                isInner = false;
                setStr();
                remove();
            }).padRight(OFFSET).size(LEN, getHeight() - OFFSET * 3).left();
            update(() -> {
                if(Vars.state.isMenu()){
                    remove();
                    isInner = false;
                    setStr();
                }
                if(disableUI)remove();
                setPosition(starter.getWidth(), (Core.graphics.getHeight() - getHeight()) / 2f);
            });
            
            new Table(Tex.clear){{
                update(() -> {
                    if(Vars.state.isMenu() || !isInner)remove();
                    if(disableUI)remove();
                });
                touchable = Touchable.enabled;
                setFillParent(true);
                addListener(new InputListener(){
                    @Override
                    public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                        point.set(Core.camera.unproject(x, y));
                        sx = String.valueOf(format(point.x / tilesize));
                        sy = String.valueOf(format(point.y / tilesize));
                        setText();
                        setFloatP();
                        return false;
                    }
                });
                Core.scene.add(this);
            }};
            Core.scene.add(this);
        }
    }
    private static class UnitSpawnTable extends Table{
        UnitSpawnTable(){
            Table in = new Table(out -> {
                out.pane(table -> {
                    int num = 0;
                    for(UnitType type : content.units()){
                        if(type.isHidden()) continue;
                        if(num % 5 == 0) table.row();
                        table.button(new TextureRegionDrawable(type.icon(Cicon.xlarge)), Styles.clearTogglei, LEN, () -> selected = type).update(b -> b.setChecked(selected == type)).size(LEN);
                        num++;
                    }
                }).fillX().height(LEN * 5f).row();
                Table t = new Table(tin -> {
                    tin.pane(con -> {
                        con.button("Switch Team", Icon.refresh, () -> {
                            player.team(player.team().id == Team.sharded.id ? state.rules.waveTeam : Team.sharded);
                        }).size(LEN * 4, LEN).update(b -> b.setColor(player.team().color));
                    }).fillX().height(LEN).row();
                    tin.pane(con -> {
                        con.button(Icon.refresh, Styles.clearTransi, () -> selectTeam = selectTeam.id == state.rules.waveTeam.id ? Team.sharded : state.rules.waveTeam).size(LEN);
                        con.button(Icon.cancel, Styles.clearTransi, () -> point.set(-1, -1)).size(LEN);
                        con.slider(1, 100, 2, spawnNum, (f) -> spawnNum = (int)f).fill().height(LEN).row();
                    }).fillX().height(LEN).row();
                    tin.pane(con -> {
                        con.button("SpawnP", Icon.link, Styles.cleart, () -> NHFunc.spawnUnit(selected, selectTeam, spawnNum, point.x, point.y)).disabled(b -> !pointValid()).size(LEN * 2, LEN);
                        con.button("SpawnC", Icon.add, Styles.cleart, () -> NHFunc.spawnUnit(selected, selectTeam, spawnNum, player.x, player.y)).size(LEN * 2, LEN);
                    }).fillX().height(LEN).row();
                    tin.pane(con -> {
                        con.button("Remove Units", Styles.cleart, Groups.unit::clear).size(LEN * 2, LEN);
                        con.button("Remove Fires", Styles.cleart, () -> {
                            for(int i = 0; i < 20; i++) Time.run(i * Time.delta * 3, Groups.fire::clear);
                        }).size(LEN * 2, LEN);
                    }).fillX().height(LEN).row();
                    tin.pane(con -> {
                        con.button("Add Items", Styles.cleart, () -> {
                            for(Item item : content.items()) player.team().core().items.add(item, 1000000);
                        }).size(LEN * 2, LEN);
                    }).fillX().height(LEN).row();
                    tin.pane(con -> {
                        con.button("Debug", Styles.cleart, () -> {
                            new TableTexDebugDialog("debug").show();
                        }).disabled(b -> !state.rules.infiniteResources && !NHSetting.getBool("@active.debug")).size(LEN * 2, LEN);
                    }).fillX().height(LEN).row();
                });
                out.pane(t).fillX().height(t.getHeight()).padTop(OFFSET).row();
            });
            pane(in).fillX().height(in.getHeight());
        }
    }
    private static final Table pTable = new Table(Tex.clear){{
        update(() -> {
            if(disableUI)remove();
            if(Vars.state.isMenu()){
                remove();
                floatTable = false;
            }else{
                if(pointValid()){
                    Vec2 v = Core.camera.project(point.x, point.y);
                    setPosition(v.x, v.y, 0);
                }else{
                    remove();
                    floatTable = false;
                }
            }
        });
        button(Icon.upOpen, Styles.emptyi, () -> {
            remove();
            floatTable = false;
        }).center();
    }};
    private static void setFloatP(){
        if(!floatTable){
            Core.scene.root.addChildAt(0, pTable);
            floatTable = true;
        }
    }
    private static final Table starter = new Table(Tex.button);
    public static final TextButton.TextButtonStyle toggletAccent = new TextButton.TextButtonStyle() {{
        this.font = Fonts.def;
        this.fontColor = Color.white;
        this.checked = Tex.buttonOver;
        this.down = Tex.buttonDown;
        this.up = Tex.button;
        this.over = Tex.buttonDown;
        this.disabled = Tex.buttonDisabled;
        this.disabledFontColor = Color.gray;
    }};
    public static final String tabSpace = "    ";
    public static final float LEN = 60f;
    public static final float OFFSET = 12f;
    public static String format(float value){return df.format(value);}
    public static String getJudge(boolean value){return value ? "[green]Yes[]" : "[red]No[]";}
    public static String getPercent(float value){return Mathf.floor(value * 100) + "%";}
    
    public static void disableTable(){
        Core.scene.root.removeChild(starter);
    }
    
    public static void showTable(){
        Core.scene.root.addChildAt(1, starter);
    }
    
    public static void tableMain(){
        if(headless || net.server())return;
        
        starter.setSize(LEN + OFFSET, (LEN + OFFSET) * 3);
        starter.setPosition(0, (Core.graphics.getHeight() - starter.getHeight()) / 2f);
    
        starter.update(() -> {
            starter.top().visible(() -> !state.isMenu() && ui.hudfrag.shown && !net.active());
            if(!state.isMenu() && ui.hudfrag.shown && !net.active())starter.touchable = Touchable.enabled;
            else starter.touchable = Touchable.disabled;
        });
        
        Player player = Vars.player;
        
        starter.table(table -> table.button(Icon.admin, Styles.clearTransi, starter.getWidth() - OFFSET, () -> {
            Table inner = new Inner();
            Table unitTable = new UnitSpawnTable();
            Table uT = new Table(){{
                Label label = new Label("<<-Spawns: [accent]" + spawnNum + "[] ->>");
                Image image = new Image();
                Label p = new Label("");
                update(() -> {
                    image.setColor(selectTeam.color);
                    label.setText(new StringBuilder().append("<<-Spawns: [accent]").append(spawnNum).append("[] ->>"));
                    p.setText(new StringBuilder().append("At: ").append(point.x).append(", ").append(point.y).append(" ->>"));
                });
                table(table1 -> {
                    add(image).growX().height(OFFSET / 3).growY().pad(OFFSET / 2).row();
                    table1.table(t -> {
                        t.add(label).row();
                        t.add(p).row();
                    }).grow().row();
                }).fillX().growY();
            }};
            inner.table(Tex.button, cont -> {
                cont.table(t -> t.add(uT) ).growX().fillY().row();
                cont.table(t -> t.add(unitTable) ).height(mobile ? inner.getHeight() : unitTable.getHeight()).growX();
            }).growX().height(mobile ? inner.getHeight() : Core.graphics.getHeight() / 1.3f);
        }).size(LEN).disabled(b -> isInner || !NHSetting.getBool("@active.admin-panel")).row()).right().padTop(OFFSET).size(LEN).row();
        Core.scene.root.addChildAt(1, starter);
    }
    
    public static void buildBulletTypeInfo(Table t, BulletType type){
        t.table(table -> {
            if(type == null)return;
            Class<?> typeClass = type.getClass();
            Field[] fields = typeClass.getFields();
            for(Field field : fields){
                try{
                    if(field.getGenericType().toString().equals("boolean")) table.add(new StringBuilder().append("[gray]").append(field.getName()).append(": ").append(getJudge(field.getBoolean(type))).append("[]")).left().row();
                    if(field.getGenericType().toString().equals("float") && field.getFloat(type) > 0) table.add(new StringBuilder().append("[gray]").append(field.getName()).append(": [accent]").append(field.getFloat(type)).append("[]")).left().row();
                    if(field.getGenericType().toString().equals("int") && field.getInt(type) > 0) table.add(new StringBuilder().append("[gray]").append(field.getName()).append(": [accent]").append(field.getInt(type)).append("[]")).left().row();
                    
                    if(field.getType().getSimpleName().equals("BulletType")){
                        BulletType inner = (BulletType)field.get(type);
                        if(inner == null || inner.toString().equals("bullet#0") || inner.toString().equals("bullet#1") || inner.toString().equals("bullet#2"))continue;
                        
                        table.add("[gray]" + field.getName() + "{ ").left().row();
                        table.table(in -> buildBulletTypeInfo(in, inner)).padLeft(LEN).row();
                        table.add("[gray]}").left().row();
                    }
                }catch(IllegalAccessException err){
                    throw new RuntimeException(err);
                }
            }
        }).row();
    }
    
    public static void tableImageShrink(TextureRegion tex, float size, Table table){
        float parma = Math.max(tex.height, tex.width);
        float f = Math.min(size, parma);
        table.image(tex).size(tex.width * f / parma, tex.height * f / parma);
    }
    
    public static void itemStack(Table parent, ItemStack stack, ItemModule itemModule){
        float size = LEN - OFFSET;
        parent.table(t -> {
            t.image(stack.item.icon(Cicon.xlarge)).size(size).left();
            t.table(n -> {
                Label l = new Label("");
                n.add(stack.item.localizedName + " ").left();
                n.add(l).left();
                n.add("/" + UI.formatAmount(stack.amount)).left().growX();
                n.update(() -> {
                    int amount = itemModule == null ? 0 : itemModule.get(stack.item);
                    l.setText(UI.formatAmount(amount));
                    l.setColor(amount < stack.amount ? Pal.redderDust : Color.white);
                });
            }).growX().height(size).padLeft(OFFSET / 2).left();
        }).growX().height(size).left().row();
    }
    
    public static void link(Table father, Links.LinkEntry link){
        father.add(new Tables.LinkTable(link)).size(Tables.LinkTable.w + OFFSET * 2f, Tables.LinkTable.h).padTop(OFFSET / 2f).row();
    }
    
    
    public static void rectSelectTable(Table parentT, Runnable run){
        NHVars.resetCtrl();
        
        Rect r = NHVars.ctrl.rect;
        
        NHVars.ctrl.isSelecting = true;
        
        NHVars.ctrl.pressDown = false;
        
        Table pTable = new Table(Tex.pane){{
            update(() -> {
                if(Vars.state.isMenu())remove();
                else{
                    Vec2 v = Core.camera.project(r.x + r.width / 2, r.y - OFFSET);
                    setPosition(v.x, v.y, 0);
                }
            });
            table(Tex.paneSolid, t -> {
                t.button(Icon.upOpen, Styles.clearFulli, () -> {
                    run.run();
                    remove();
                    NHVars.ctrl.isSelecting = false;
                }).size(LEN * 4, LEN).disabled(b -> NHVars.ctrl.pressDown);
            }).size(LEN * 4, LEN);
        }};
        
        Table floatTable = new Table(Tex.clear){{
            parentT.color.a = 0.3f;
            
            update(() -> {
                r.setSize(Math.abs(NHVars.ctrl.to.x - NHVars.ctrl.from.x), Math.abs(NHVars.ctrl.to.y - NHVars.ctrl.from.y)).setCenter((NHVars.ctrl.from.x + NHVars.ctrl.to.x) / 2f, (NHVars.ctrl.from.y + NHVars.ctrl.to.y) / 2f);
                
                if(Vars.state.isMenu() || !NHVars.ctrl.isSelecting){
                    NHVars.ctrl.from.set(0, 0);
                    NHVars.ctrl.to.set(0, 0);
                    remove();
                }
                
                if(!mobile && NHVars.ctrl.pressDown){
                    NHVars.ctrl.to.set(Core.camera.unproject(Core.input.mouse())).clamp(0, 0, world.unitHeight(), world.unitWidth());
                }
            });
            
            touchable = Touchable.enabled;
            setFillParent(true);
            if(mobile){
                addListener(new InputListener(){
                    public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                    if(!NHVars.ctrl.pressDown){
                        touchable = Touchable.enabled;
                        NHVars.ctrl.from.set(Core.camera.unproject(x, y)).clamp(-finalWorldBounds, -finalWorldBounds, world.unitHeight() + finalWorldBounds, world.unitWidth() + finalWorldBounds);
                        NHVars.ctrl.to.set(NHVars.ctrl.from);
                    }else{
                        NHVars.ctrl.to.set(Core.camera.unproject(x, y)).clamp(-finalWorldBounds, -finalWorldBounds, world.unitHeight() + finalWorldBounds, world.unitWidth() + finalWorldBounds);
                    }
                    NHVars.ctrl.pressDown = !NHVars.ctrl.pressDown;
                    return false;
                    }
                });
            }else addListener(new InputListener(){
                public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                    parentT.touchable = Touchable.childrenOnly;
                    NHVars.ctrl.pressDown = true;
                    NHVars.ctrl.from.set(Core.camera.unproject(x, y)).clamp(-finalWorldBounds, -finalWorldBounds, world.unitHeight() + finalWorldBounds, world.unitWidth() + finalWorldBounds);
                    NHVars.ctrl.to.set(NHVars.ctrl.from);
                    return false;
                }
                
                public void exit(InputEvent event, float x, float y, int pointer, Element toActor) {
                    if(remove()){
                        parentT.touchable = Touchable.enabled;
                        run.run();
                        NHVars.resetCtrl();
                    }
                }
            });
        }
            
            @Override
            public boolean remove(){
                parentT.color.a = 1f;
                return super.remove();
            }
        };
        
        Core.scene.root.addChildAt(9, floatTable);
        if(mobile)Core.scene.root.addChildAt(10, pTable);
    }
    
    public static void pointSelectTable(Table parent, Cons<Point2> cons){
        NHVars.resetCtrl();
        NHVars.ctrl.isSelecting = true;
        
        Table floatTable = new Table(Tex.clear){{
            update(() -> {
                if(Vars.state.isMenu())remove();
            });
            touchable = Touchable.enabled;
            setFillParent(true);
            
            addListener(new InputListener(){
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                NHVars.ctrl.ctrlVec2.set(Core.camera.unproject(x, y)).clamp(-Vars.finalWorldBounds, -Vars.finalWorldBounds, world.unitHeight() + Vars.finalWorldBounds, world.unitWidth() + Vars.finalWorldBounds);
                return false;
                }
            });
        }};
        
        Table pTable = new Table(Tex.clear){{
            update(() -> {
                if(Vars.state.isMenu()){
                    remove();
                }else{
                    Vec2 v = Core.camera.project(World.toTile(NHVars.ctrl.ctrlVec2.x) * tilesize, World.toTile(NHVars.ctrl.ctrlVec2.y) * tilesize);
                    setPosition(v.x, v.y, 0);
                }
            });
            button(Icon.cancel, Styles.emptyi, () -> {
                cons.get(Tmp.p1.set(World.toTile(NHVars.ctrl.ctrlVec2.x), World.toTile(NHVars.ctrl.ctrlVec2.y)));
                NHVars.ctrl.isSelecting = false;
                remove();
                floatTable.remove();
            }).center();
        }};
        
        Core.scene.root.addChildAt(Math.max(parent.getZIndex() - 1, 0), pTable);
        Core.scene.root.addChildAt(Math.max(parent.getZIndex() - 2, 0), floatTable);
    }
    
    private static void scheduleToast(Runnable run){
        long duration = (int)(3.5 * 1000);
        long since = Time.timeSinceMillis(lastToast);
        if(since > duration){
            lastToast = Time.millis();
            run.run();
        }else{
            Time.runTask((duration - since) / 1000f * 60f, run);
            lastToast += duration;
        }
    }
    
    public static void showToast(Drawable icon, String text, Sound sound){
        if(state.isMenu()) return;
        
        scheduleToast(() -> {
            sound.play();
            
            Table table = new Table(Tex.button);
            table.update(() -> {
                if(state.isMenu() || !ui.hudfrag.shown){
                    table.remove();
                }
            });
            table.margin(12);
            table.image(icon).pad(3);
            table.add(text).wrap().width(280f).get().setAlignment(Align.center, Align.center);
            table.pack();
            
            //create container table which will align and move
            Table container = Core.scene.table();
            container.top().add(table);
            container.setTranslation(0, table.getPrefHeight());
            container.actions(Actions.translateBy(0, -table.getPrefHeight(), 1f, Interp.fade), Actions.delay(2.5f),
                    //nesting actions() calls is necessary so the right prefHeight() is used
                    Actions.run(() -> container.actions(Actions.translateBy(0, table.getPrefHeight(), 1f, Interp.fade), Actions.remove())));
        });
    }
}