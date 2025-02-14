package newhorizon.func;

import arc.func.Boolf;
import arc.func.Intc2;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Geometry;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.pooling.Pools;
import mindustry.entities.Effect;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;
import newhorizon.block.special.JumpGate;
import newhorizon.vars.NHVars;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static mindustry.Vars.*;
import static mindustry.core.World.toTile;

public class NHFunc{
    private static final float MAX_TELEPORT_DST_NET = tilesize / 2f;
    private static Tile tileParma;
    private static Floor floorParma;
    private static final Seq<Tile> tiles = new Seq<>();
    private static final IntSeq buildingIDSeq = new IntSeq();
    private static final int maxCompute = 32;
    private static final Rand rand = new Rand(0);
    public static final Effect debugEffect = new Effect(120f, 300f, e -> {
        if(!(e.data instanceof Seq))return;
        Seq<Rect> data = e.data();
        Draw.color(Pal.lancerLaser);
        Draw.z(Layer.flyingUnit + 2f);
        for(Rect r : data){
            r.getCenter(Tmp.v1);
            Fill.square(Tmp.v1.x, Tmp.v1.y, tilesize / 2f);
        }
    });
    private static final Vec2 point1 = new Vec2(), point2 = new Vec2(), point3 = new Vec2(), point4 = new Vec2();
    private static final Rect r1 = new Rect(), r2 = new Rect();
    
    public static long seedNet(){
        return Groups.sync.size() + state.wave + state.stats.timeLasted;
    }
    
    public static Unit teleportUnitNet(Unit before, float x, float y, float angle, @Nullable Player player){
        if(net.active() || headless){
            float dst = before.dst(x, y);
            int sigs = Mathf.ceil(dst / MAX_TELEPORT_DST_NET);
            point1.set(before);
            point2.trns(angle, dst / sigs);
            
            for(int i = 1; i < sigs; i++){
                point3.set(point2).scl(i).add(point1);
                Time.runTask(0.00075f * (i - 1), () -> {
                    if(player != null){
                        player.set(point3);
                        player.snapInterpolation();
                        player.snapSync();
                        player.lastUpdated = player.updateSpacing = 0;
                    }
                    before.set(point3);
                    before.snapInterpolation();
                    before.snapSync();
                    before.updateSpacing = 0;
                    before.lastUpdated = 0;
                });
            }
        }else{
            before.set(x, y);
        }
        before.rotation = angle;
        return before;
    }
    
    public static void square(int x, int y, int radius, Intc2 cons) {
        for(int dx = -radius; dx <= radius; ++dx) {
            for(int dy = -radius; dy <= radius; ++dy) {
                cons.get(dx + x, dy + y);
            }
        }
    }
    
    /**
     * @implNote Get all the {@link Tile} {@code tile} within a certain range at certain position.
     * @param x the abscissa of search center.
     * @param y the ordinate of search center.
     * @param range the search range.
     * @param bool {@link Boolf} {@code lambda} to determine whether the condition is true.
     * @return {@link Seq}{@code <Tile>} - which contains eligible {@link Tile} {@code tile}.
     */
    public static Seq<Tile> getAcceptableTiles(int x, int y, int range, Boolf<Tile> bool){
        Seq<Tile> tiles = new Seq<>(true, (int)(Mathf.pow(range, 2) * Mathf.pi), Tile.class);
        Geometry.circle(x, y, range, (x1, y1) -> {
            if((tileParma = world.tile(x1, y1)) != null && bool.get(tileParma)){
                tiles.add(world.tile(x1, y1));
            }
        });
        return tiles;
    }
    
    private static void clearTmp(){
        tileParma = null;
        floorParma = null;
        buildingIDSeq.clear();
        tiles.clear();
    }
    
    public static int getTeamIndex(Team team){return NHVars.allTeamSeq.indexOf(team);}
    
    @Contract(value = "!null, _ -> param1", pure = true)
    public static Color getColor(Color defaultColor, Team team){
        return defaultColor == null ? team.color : defaultColor;
    }
    
    //not support server
    public static void spawnUnit(UnitType type, Team team, int spawnNum, float x, float y){
        for(int spawned = 0; spawned < spawnNum; spawned++){
            Time.run(spawned * Time.delta, () -> {
                Unit unit = type.create(team);
                if(unit != null){
                    unit.set(x, y);
                    unit.add();
                }else Log.info("Unit == null");
            });
        }
    }
    
    @Contract(pure = true)
    public static float regSize(@NotNull UnitType type){
        return type.hitSize / tilesize / tilesize / 3.25f;
    }
    
    /**[1]For flying, [2] for navy, [3] for ground */
    public static Seq<Boolf<Tile>> formats(){
        Seq<Boolf<Tile>> seq = new Seq<>(3);
        
        seq.add(
            tile -> world.getQuadBounds(Tmp.r1).contains(tile.getBounds(Tmp.r2)),
            tile -> tile.floor().isLiquid && !tile.cblock().solid && !tile.floor().solid && !tile.overlay().solid && !tile.block().solidifes,
            tile -> !tile.floor().isDeep() && !tile.cblock().solid && !tile.floor().solid && !tile.overlay().solid && !tile.block().solidifes
        );
        
        return seq;
    }
    
    public static Boolf<Tile> ableToSpawn(UnitType type){
        Boolf<Tile> boolf;
        
        Seq<Boolf<Tile>> boolves = formats();
        
        if(type.flying){
            boolf = boolves.get(0);
        }else if(WaterMovec.class.isAssignableFrom(type.constructor.get().getClass())){
            boolf = boolves.get(1);
        }else{
            boolf = boolves.get(2);
        }
        
        return boolf;
    }
    
    public static Seq<Tile> ableToSpawn(UnitType type, float x, float y, float range){
        Seq<Tile> tSeq = new Seq<>(Tile.class);
    
        Boolf<Tile> boolf = ableToSpawn(type);
        
        return tSeq.addAll(getAcceptableTiles(toTile(x), toTile(y), toTile(range), boolf));
    }
    
    public static boolean ableToSpawnPoints(Seq<Vec2> spawnPoints, UnitType type, float x, float y, float range, int num, long seed){
        Seq<Tile> tSeq = ableToSpawn(type, x, y, range);
    
        rand.setSeed(seed);
        for(int i = 0; i < num; i++){
            Tile[] positions = tSeq.shrink();
            if(positions.length < num)return false;
            spawnPoints.add(new Vec2().set(positions[rand.nextInt(positions.length)]));
        }
        
        return true;
    }
    
    public static boolean spawnUnit(Building starter, float x, float y, float angle, float spawnRange, float spawnReloadTime, float spawnDelay, UnitType type, int spawnNum){
        if(type == null)return false;
        clearTmp();
        Seq<Vec2> vectorSeq = new Seq<>();
        
        if(!ableToSpawnPoints(vectorSeq, type, x, y, spawnRange, spawnNum, Mathf.random(-100, 100)))return false;
        
        int i = 0;
        for (Vec2 s : vectorSeq) {
            JumpGate.Spawner spawner = Pools.obtain(JumpGate.Spawner.class, JumpGate.Spawner::new);
            spawner.init(type, spawnNum, starter.team(), s, angle, spawnReloadTime + i * spawnDelay, starter.pos());
            if(!net.client())spawner.add();
            i++;
        }
        return true;
    }
}
