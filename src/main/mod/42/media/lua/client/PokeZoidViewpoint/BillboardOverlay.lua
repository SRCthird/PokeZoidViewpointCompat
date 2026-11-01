require 'ISUI/ISPanel'
require 'PokeZoidViewpoint/Bridge'
local B = PokeZoidViewpoint
B.billboards = B.billboards or {}
B.maxDrawDistance = 34
B.DEBUG_XRAY = false

-- Bounds on apparent size, even when a sprite is beside/behind the camera.
B.spriteNearDistance = 1.8
B.spriteHeightAtOneTile = 270
B.spriteMinHeightPx = 12
B.spriteMaxHeightScreenFraction = 0.10

-- PokeZoid scan pickups
B.drawScanPickups = true
B.ballHeightAtOneTile = 1500
B.ballNearDistance = 0.75
B.ballMinHeightPx = 100
B.ballMaxHeightPx = 1000

-- Pokemon appearance
B.spriteScale = 1.50
B.spriteHeightOffset = 0.05
B.spriteGroundAnchor = 0.88

local function currentMode()
    local api = Viewpoint and Viewpoint.View
    if not api then return 'unavailable' end
    local ok, nextMode = pcall(function() return api.next() end)
    if not ok then return 'read-failed' end
    if nextMode == 'third' then return 'first' end
    if nextMode == 'isometric' then return 'third' end
    if nextMode == 'first' then return 'isometric' end
    return 'unknown:' .. tostring(nextMode)
end

local function enabled()
    local mode = currentMode()
    return mode == 'first' or mode == 'third'
end

local function spriteOf(s)
    if not s or s.kind then return nil end
    local species = CreatureViewer and CreatureViewer.Species and CreatureViewer.Species[s.speciesId]
    if not species then return nil end
    local path = CreatureViewer.getWorldCreatureTexturePath(species, s.chromatic == true)
    if not path then return nil end
    local texture = CreatureViewer.getPixelTexture(path)
    if texture then CreatureViewer.preparePixelTexture(texture) end
    return texture
end

local function scanPickupTexture(s)
    local item = CreatureViewer and CreatureViewer.ScanItems and CreatureViewer.ScanItems[s.kind]
    if not item or not item.texture then return nil end
    local path = item.texture
    local filename = path:match('([^/]+)$')
    if filename then
        local padded = 'media/textures/CreatureViewer/grounded_scan_world_' .. filename
        if getTexture(padded) then path = padded end
    end
    local tex = (s.kind == 'egg' and CreatureViewer.getPixelTexture and
        CreatureViewer.getPixelTexture(path)) or getTexture(path)
    if tex and CreatureViewer.preparePixelTexture then CreatureViewer.preparePixelTexture(tex) end
    return tex
end

local function isoVisible(s, player)
    if B.DEBUG_XRAY then return true end
    local cell = getCell()
    if not cell then return false end
    local square = cell:getGridSquare(math.floor(s.x), math.floor(s.y),
        math.floor(s.z or player:getZ()))
    if not square then return false end
    local index = player:getPlayerNum()
    local ok, couldSee = pcall(function() return square:isCouldSee(index) end)
    if not ok or not couldSee then return false end
    return true
end

local function isScanPickup(s)
    return s and s.kind ~= nil and s.kind ~= false and
        CreatureViewer and CreatureViewer.ScanItems and
        CreatureViewer.ScanItems[s.kind] ~= nil
end

local function projectedHeight(s, player, viewportHeight)
    local dx = s.x - player:getX()
    local dy = s.y - player:getY()
    local horizontal = math.sqrt(dx * dx + dy * dy)
    if isScanPickup(s) then
        local distance = math.max(B.ballNearDistance, horizontal)

        return math.max(
            B.ballMinHeightPx,
            math.min(
                B.ballMaxHeightPx,
                B.ballHeightAtOneTile / distance
            )
        )
    end

    local distance = math.max(B.spriteNearDistance, horizontal)
    return math.max(B.spriteMinHeightPx,
        math.min(viewportHeight * B.spriteMaxHeightScreenFraction,
            B.spriteHeightAtOneTile * B.spriteScale / distance))
end

local function nearCreatures()
    local items = {}
    local p = getPlayer()
    if not p then return items end
    local function add(s)
        if not s or not s.x or not s.y or s.z == nil then return end
        if s.kind and (not B.drawScanPickups or not isScanPickup(s)) then return end
        local dx,dy=s.x-p:getX(),s.y-p:getY()
        if dx*dx+dy*dy <= B.maxDrawDistance*B.maxDrawDistance then items[#items+1]=s end
    end
    B.eachCreature(add)
    local world = CreatureWorldSightings
    if B.drawScanPickups and world and world.activeMarkers then
        local present = {}
        for _, s in ipairs(items) do present[s.sightingId or s] = true end
        for _, s in ipairs(world.activeMarkers) do
            local key = s.sightingId or s
            if isScanPickup(s) and not present[key] then
                local before = #items
                add(s)
                if #items > before then present[key] = true end
            end
        end
    end

    local F=CreatureViewerFollower
    if F and F.active then add(F.active) end
    return items
end

local Overlay=ISPanel:derive('PokeZoidViewpointSpriteOverlay')
function Overlay:new()
    local o=ISPanel.new(self,0,0,0,0)
    o.background=false; o.border=false; o:setAlwaysOnTop(false)
    return o
end

function Overlay:prerender()
    self.viewportWidth = getCore():getScreenWidth()
    self.viewportHeight = getCore():getScreenHeight()
end

function Overlay:render()
    B.billboards = {}
    if not enabled() then return end
    local project = PZVPProject
    if type(project) ~= 'function' then
        if not self.bridgeWarning then
            self.bridgeWarning = true
            print('[PokeZoidViewpoint] PZVPProject unavailable: check Java JAR registration and restart')
        end
        return
    end
    local p = getPlayer()
    if not p then return end
    local w = self.viewportWidth or getCore():getScreenWidth()
    local h = self.viewportHeight or getCore():getScreenHeight()
    if w < 1 or h < 1 then return end

    local native = type(PZVPSubmit) == 'function'
        and type(PZVPBegin) == 'function'
        and type(PZVPNativeReady) == 'function'
        and PZVPNativeReady()
    if not native then
        B.billboards = {}
        return
    end
    PZVPBegin()

    local candidates = nearCreatures()
    local list = {}
    local projected, textured, occluded, pickupCount = 0, 0, 0, 0
    for _, s in ipairs(candidates) do
        if isScanPickup(s) then pickupCount = pickupCount + 1 end
        if not isoVisible(s, p) then
            occluded = occluded + 1
        else
            local wx = s.x + 0.5
            local wy = s.y + 0.5
            local wz = s.z or p:getZ()
            local offset = isScanPickup(s) and 0.02 or B.spriteHeightOffset
            local groundX, groundY, groundDepth, ndcZ = project(wx, wy, wz,
                offset, w, h)
            if groundX and groundY and groundDepth then
                projected = projected + 1
                local tex = isScanPickup(s) and scanPickupTexture(s) or spriteOf(s)
                if tex then
                    textured = textured + 1
                    local tw, th = tex:getWidth(), tex:getHeight()
                    if tw > 0 and th > 0 then
                        local height = projectedHeight(s, p, h)
                        local width = height * tw / th
                        local x = groundX - width * 0.5
                        local anchor = isScanPickup(s) and 0.92 or B.spriteGroundAnchor
                        local y = groundY - height * anchor
                        if height > 1 and x + width > 0 and x < w
                           and y + height > 0 and y < h then
                            list[#list + 1] = {
                                s=s, tex=tex, x=x, y=y, w=width, h=height,
                                depth=groundDepth, ndcZ=ndcZ
                            }
                        end
                    end
                end
            end
        end
    end

    local now = getTimestampMs and getTimestampMs() or 0
    if now - (self.lastDebugTime or -5000) >= 5000 then
        self.lastDebugTime = now
        print(string.format(
            '[PokeZoidViewpoint] native billboards: nearby=%d pickups=%d occluded=%d projected=%d textures=%d visible=%d',
            #candidates, pickupCount, occluded, projected, textured, #list))
    end
    table.sort(list, function(a,b) return a.depth > b.depth end)
    for _, v in ipairs(list) do
        if v.ndcZ then
            PZVPSubmit(v.tex, v.x, v.y, v.w, v.h, v.ndcZ,
                w, h, B.DEBUG_XRAY)
            B.billboards[#B.billboards+1] = v
        end
    end
end

function B.screenCreature(x,y)
    for i=#B.billboards,1,-1 do
        local v=B.billboards[i]
        if x>=v.x and x<=v.x+v.w and y>=v.y and y<=v.y+v.h then return v.s end
    end
    return nil
end

local overlay
local function setup()
    if overlay or not getPlayer() then return end
    overlay = Overlay:new()
    overlay:initialise()
    overlay:instantiate()
    overlay:addToUIManager()
    overlay:setCapture(false)
end
Events.OnCreatePlayer.Add(setup)
Events.OnTick.Add(setup)

print('[PokeZoidViewpoint] Sprite collector loaded.')
