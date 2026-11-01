require 'CreatureViewer/CreatureWorldSightings'
require 'CreatureViewer/CreatureFollower'
require 'CreatureViewer/CreatureWorldInteraction'

PokeZoidViewpoint = PokeZoidViewpoint or {}
local B = PokeZoidViewpoint
B.version = '${modVersion}'
B.projectWorld = B.projectWorld or nil
B.targetWorld = B.targetWorld or nil
B.lastTarget = nil

local function active()
    local p = getPlayer()
    if not p or not CreatureWorldSightings then return false end
    if CreatureViewerParticipation and CreatureViewerParticipation.isActive then
        return CreatureViewerParticipation.isActive(p)
    end
    return true
end

function B.eachCreature(visitor)
    if not active() then return end
    local seen = {}
    local function add(s)
        if not s or not s.x or not s.y or not s.z then return end
        local key = s.sightingId or s
        if seen[key] then return end
        seen[key] = true
        visitor(s)
    end
    for _, s in ipairs(CreatureWorldSightings.activeMarkers or {}) do add(s) end
    for _, s in pairs(CreatureWorldSightings.sharedSightings or {}) do add(s) end
end

function B.findNearest(x,y,z,radius)
    local best, distanceSq
    radius = radius or 1.6
    B.eachCreature(function(s)
        local dz = math.abs((s.z or 0)-z)
        if dz <= 0.75 then
            local dx,dy = s.x-x,s.y-y
            local d=dx*dx+dy*dy
            if d<=radius*radius and (not distanceSq or d<distanceSq) then
                best,distanceSq=s,d
            end
        end
    end)
    return best
end

function B.populateNativeMenu(playerIndex, menu, sighting)
    if not sighting or not menu then return false end
    local cell=getCell()
    local sq=cell and cell:getGridSquare(math.floor(sighting.x),math.floor(sighting.y),math.floor(sighting.z))
    if not sq then return false end
    if not triggerEvent then return false end
    local objects={sq}
    objects[1]=sq:getObjects() and sq:getObjects():size()>0 and sq:getObjects():get(0) or {getSquare=function() return sq end}
    local ok,err=pcall(triggerEvent,'OnFillWorldObjectContextMenu',playerIndex,menu,objects,false)
    if not ok then print('[PokeZoidViewpoint] menu callback failed: '..tostring(err)) end
    return ok
end

function B.getTarget()
    if not active() or not B.targetWorld then return nil end
    local ok,x,y,z=pcall(B.targetWorld)
    if not ok or type(x)~='number' or type(y)~='number' or type(z)~='number' then return nil end
    return B.findNearest(x,y,z)
end

function B.openSightingContextMenu(s,screenX,screenY)
    if not s then return false end
    local p=getPlayer()
    if not p or not ISContextMenu then return false end
    local context=ISContextMenu.get(p:getPlayerNum(),screenX,screenY)
    if not B.populateNativeMenu(p:getPlayerNum(),context,s) then return false end
    if context.numOptions == nil or context.numOptions <= 1 then return false end
    context:ensureVisible()
    return true
end

function B.openTargetContextMenu(screenX,screenY)
    return B.openSightingContextMenu(B.getTarget(),screenX,screenY)
end

print('[PokeZoidViewpoint] Client bridge loaded; native Viewpoint rendering/hover integration requires a camera and interaction API binding.')
