package letrain.mvp.impl.services;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import letrain.ground.GroundMap;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.track.CargoTypes;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;

/**
 * Service responsible for moving track elements (stations, sensors, signals) along rails
 * and computing industrial roles from ground terrain.
 */
@JsonIgnoreType
public final class TrackElementMovementService {

    private TrackElementMovementService() {}

    private record MoveResult(Track destination, Dir heading) {}

    public static boolean moveSensor(Model model, Sensor sensor, Dir dir) {
        Track origin = sensor.getTrack();
        if (origin == null) {
            return false;
        }
        MoveResult result = findMoveDestination(origin, dir);
        if (result == null) {
            return false;
        }
        relocateSensor(sensor, origin, result.destination);
        if (sensor instanceof Station) {
            applyStationRoleByIndustry(model.getGroundMap(), (Station) sensor, result.destination.getPosition());
        }
        model.setMapChanged(true);
        return true;
    }

    public static boolean moveSensorForward(Model model, Sensor sensor) {
        Track origin = sensor.getTrack();
        if (origin == null) {
            return false;
        }
        Dir front = sensor.getCreationDir();
        if (front == null) {
            return false;
        }
        MoveResult result = findMoveDestination(origin, front);
        if (result == null) {
            return false;
        }
        relocateSensor(sensor, origin, result.destination);
        sensor.setCreationDir(continuationDir(result.destination, result.heading));
        if (sensor instanceof Station) {
            applyStationRoleByIndustry(model.getGroundMap(), (Station) sensor, result.destination.getPosition());
        }
        model.setMapChanged(true);
        return true;
    }

    public static boolean moveSensorBackward(Model model, Sensor sensor) {
        Track origin = sensor.getTrack();
        if (origin == null) {
            return false;
        }
        Dir front = sensor.getCreationDir();
        if (front == null) {
            return false;
        }
        Dir back = backEndDir(origin, front);
        if (back == null) {
            return false;
        }
        MoveResult result = findMoveDestination(origin, back);
        if (result == null) {
            return false;
        }
        relocateSensor(sensor, origin, result.destination);
        sensor.setCreationDir(result.heading.inverse());
        if (sensor instanceof Station) {
            applyStationRoleByIndustry(model.getGroundMap(), (Station) sensor, result.destination.getPosition());
        }
        model.setMapChanged(true);
        return true;
    }

    public static void relocateSensor(Sensor sensor, Track origin, Track destination) {
        origin.setComponent(null);
        sensor.setTrack(destination);
        destination.setComponent(sensor);
    }

    public static Dir continuationDir(Track destination, Dir arrivalDir) {
        Dir exit = destination.getDir(arrivalDir.inverse());
        return exit != null ? exit : arrivalDir;
    }

    public static Dir backEndDir(Track track, Dir front) {
        List<Dir> connected = track.getConnections();
        if (connected.contains(front)) {
            for (Dir d : connected) {
                if (d != front) {
                    return d;
                }
            }
            return null;
        }
        Dir opposite = front.inverse();
        return track.getConnected(opposite) != null ? opposite : null;
    }

    public static MoveResult findMoveDestination(Track origin, Dir dir) {
        Track cursor = origin;
        Dir heading = dir;
        Set<Track> visited = new HashSet<>();
        visited.add(origin);
        while (true) {
            Track next = cursor.getConnected(heading);
            if (next == null || !visited.add(next)) {
                return null;
            }
            if (next.getLinker() != null) {
                return null;
            }
            if (next instanceof ForkRailTrack) {
                Dir exit = next.getDir(heading.inverse());
                if (exit == null) {
                    return null;
                }
                cursor = next;
                heading = exit;
            } else {
                if (next.getComponent() == null) {
                    return new MoveResult(next, heading);
                }
                Dir exit = next.getDir(heading.inverse());
                if (exit == null) {
                    return null;
                }
                cursor = next;
                heading = exit;
            }
        }
    }

    public static void applyStationRoleByIndustry(GroundMap groundMap, Station station, Point position) {
        if (groundMap == null) {
            return;
        }
        Integer terrain = groundMap.findClosestIndustry(position, 5);
        if (terrain != null) {
            int density = groundMap.countIndustryDensity(position, 5, terrain);
            station.setCargoType(CargoTypes.IndustryMapper.getCargoForTerrain(terrain));
            station.setRole(CargoTypes.IndustryMapper.getRoleForTerrain(terrain));
            station.setIndustryCount(density);
            if (station.getRole() == CargoTypes.StationRole.PRODUCER) {
                station.setStorage(50);
            }
        } else {
            station.setCargoType(CargoTypes.NONE);
            station.setRole(CargoTypes.StationRole.GENERIC);
            station.setIndustryCount(0);
            station.setStorage(0);
        }
    }

    public static CargoTypes getStationGhostCargoType(GroundMap groundMap, Point cursorPosition) {
        if (groundMap == null || cursorPosition == null) {
            return CargoTypes.NONE;
        }
        Integer terrain = groundMap.findClosestIndustry(cursorPosition, 5);
        if (terrain != null) {
            return CargoTypes.IndustryMapper.getCargoForTerrain(terrain);
        }
        return CargoTypes.NONE;
    }

    public static CargoTypes.StationRole getStationGhostRole(GroundMap groundMap, Point cursorPosition) {
        if (groundMap == null || cursorPosition == null) {
            return CargoTypes.StationRole.GENERIC;
        }
        Integer terrain = groundMap.findClosestIndustry(cursorPosition, 5);
        if (terrain != null) {
            return CargoTypes.IndustryMapper.getRoleForTerrain(terrain);
        }
        return CargoTypes.StationRole.GENERIC;
    }
}
