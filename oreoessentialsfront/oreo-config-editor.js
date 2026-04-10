// OreoEssentials Config Editor - Complete JavaScript
// All sections and config generation

const sections = {
    server: {
        title: 'Server & Language Settings',
        content: `
            <div class="info-box">
                <p><strong>Server Name:</strong> Give each server a unique name for cross-server homes (e.g., "survival-1", "creative-1", "lobby")</p>
            </div>
            
            <div class="form-grid">
                <div class="form-group">
                    <label>Server Name <span class="hint">(unique identifier)</span></label>
                    <input type="text" id="serverName" value="survival-1" placeholder="survival-1">
                </div>
                
                <div class="form-group">
                    <label>Language</label>
                    <select id="language">
                        <option value="en" selected>English</option>
                        <option value="fr">Français</option>
                        <option value="es">Español</option>
                        <option value="de">Deutsch</option>
                        <option value="pt">Português</option>
                    </select>
                </div>
            </div>
        `
    },
    
    features: {
        title: 'Features & Debug Settings',
        content: `
            <div class="subsection">
                <h3>General Features</h3>
                <div class="checkbox-group">
                    <input type="checkbox" id="debugMode">
                    <label for="debugMode">Enable Debug Mode</label>
                </div>
                
                <div class="checkbox-group">
                    <input type="checkbox" id="placeholderDebug">
                    <label for="placeholderDebug">Placeholder Debug</label>
                </div>
                
                <div class="checkbox-group">
                    <input type="checkbox" id="tradeDebug" checked>
                    <label for="tradeDebug">Trade Debug</label>
                </div>
            </div>
            
            <div class="subsection">
                <h3>TPA Settings</h3>
                <div class="checkbox-group">
                    <input type="checkbox" id="tpaDebug">
                    <label for="tpaDebug">TPA Debug</label>
                </div>
                
                <div class="checkbox-group">
                    <input type="checkbox" id="tpaDebugEcho">
                    <label for="tpaDebugEcho">TPA Debug Echo to Player</label>
                </div>
            </div>
            
            <div class="subsection">
                <h3>Skins</h3>
                <div class="checkbox-group">
                    <input type="checkbox" id="skinsDebug">
                    <label for="skinsDebug">Skins Debug</label>
                </div>
            </div>
        `
    },
    
    storage: {
        title: 'Data Storage Configuration',
        content: `
            <div class="info-box">
                <p><strong>Storage Types:</strong> YAML/JSON for single server | MongoDB for cross-server homes & data sharing</p>
            </div>
            
            <div class="subsection">
                <h3>Essentials Storage (Homes, Warps, Spawn)</h3>
                <div class="form-group">
                    <label>Storage Type</label>
                    <select id="essentialsStorage" onchange="toggleMongoFields()">
                        <option value="yaml" selected>YAML (Local only)</option>
                        <option value="json">JSON (Local only)</option>
                        <option value="mongodb">MongoDB (Cross-server)</option>
                    </select>
                </div>
            </div>
            
            <div class="subsection" id="mongoConfig" style="display:none;">
                <h3>MongoDB Configuration</h3>
                <div class="form-grid">
                    <div class="form-group full-width">
                        <label>Connection URI</label>
                        <input type="text" id="mongoUri" value="mongodb://localhost:27017" placeholder="mongodb://host:port">
                    </div>
                    
                    <div class="form-group">
                        <label>Database Name</label>
                        <input type="text" id="mongoDatabase" value="oreo">
                    </div>
                    
                    <div class="form-group">
                        <label>Collection Prefix</label>
                        <input type="text" id="mongoPrefix" value="oreo_">
                    </div>
                </div>
                
                <div class="info-box">
                    <p><strong>Collections created:</strong> prefix_homes, prefix_warps, prefix_meta, prefix_home_directory</p>
                </div>
            </div>
        `
    },
    
    network: {
        title: 'Network & Cross-Server',
        content: `
            <div class="subsection">
                <h3>Cross-Server Transport</h3>
                <div class="checkbox-group">
                    <input type="checkbox" id="crossServer" checked>
                    <label for="crossServer">Enable Cross-Server Features</label>
                </div>
                
                <div class="form-group">
                    <label>Transport Method</label>
                    <select id="transport">
                        <option value="plugin_message" selected>Plugin Message (Bungee/Velocity)</option>
                    </select>
                </div>
            </div>
            
            <div class="subsection">
                <h3>Inventory See Settings</h3>
                <div class="checkbox-group">
                    <input type="checkbox" id="invseeAllowEdit" checked>
                    <label for="invseeAllowEdit">Allow editing inventory while player is online elsewhere</label>
                </div>
            </div>
            
            <div class="subsection">
                <h3>RabbitMQ (Optional - Advanced)</h3>
                <div class="info-box">
                    <p>Need RabbitMQ? Contact us on Discord for $2/month hosting</p>
                </div>
                
                <div class="checkbox-group">
                    <input type="checkbox" id="rabbitmqEnabled">
                    <label for="rabbitmqEnabled">Enable RabbitMQ</label>
                </div>
                
                <div class="form-group full-width">
                    <label>RabbitMQ URI</label>
                    <input type="text" id="rabbitmqUri" value="amqps://user:pass@host/vhost">
                </div>
            </div>
        `
    },
    
    homes: {
        title: 'Homes & Warps',
        content: `
            <div class="form-grid">
                <div class="form-group">
                    <label>Max Homes Per Player (Default)</label>
                    <input type="number" id="maxHomes" value="5" min="1">
                </div>
                
                <div class="form-group">
                    <label></label>
                    <div class="checkbox-group">
                        <input type="checkbox" id="permissionBased" checked>
                        <label for="permissionBased">Use Permission-Based Limits (oreo.homes.N)</label>
                    </div>
                </div>
            </div>
            
            <div class="info-box">
                <p><strong>Permissions:</strong> oreo.homes.5, oreo.homes.10, etc. - highest permission wins</p>
            </div>
        `
    },
    
    tpa: {
        title: 'Teleport Requests (TPA)',
        content: `
            <div class="form-group">
                <label>Request Timeout (Seconds)</label>
                <input type="number" id="tpaTimeout" value="60" min="10">
            </div>
            
            <div class="info-box">
                <p>How long before a /tpa request expires automatically</p>
            </div>
        `
    },
    
    playersync: {
        title: 'Player Sync (Cross-Server)',
        content: `
            <div class="info-box">
                <p>Default sync settings for new players. Players can toggle with /sync command.</p>
            </div>
            
            <div class="subsection">
                <h3>Sync Options</h3>
                <div class="checkbox-group">
                    <input type="checkbox" id="syncInventory" checked>
                    <label for="syncInventory">Inventory</label>
                </div>
                
                <div class="checkbox-group">
                    <input type="checkbox" id="syncXp" checked>
                    <label for="syncXp">Experience (XP)</label>
                </div>
                
                <div class="checkbox-group">
                    <input type="checkbox" id="syncHealth" checked>
                    <label for="syncHealth">Health</label>
                </div>
                
                <div class="checkbox-group">
                    <input type="checkbox" id="syncHunger" checked>
                    <label for="syncHunger">Hunger</label>
                </div>
            </div>
        `
    },
    
    vaults: {
        title: 'Player Vaults',
        content: `
            <div class="form-grid">
                <div class="form-group">
                    <label>Storage Type</label>
                    <select id="vaultStorage">
                        <option value="auto" selected>Auto (follows essentials.storage)</option>
                        <option value="yaml">YAML</option>
                        <option value="mongodb">MongoDB</option>
                    </select>
                </div>
                
                <div class="form-group">
                    <label>Collection Name (MongoDB)</label>
                    <input type="text" id="vaultCollection" value="oreo_playervaults">
                </div>
                
                <div class="form-group">
                    <label>Max Slots Per Vault</label>
                    <input type="number" id="vaultSlotsCap" value="54" min="1" max="54">
                </div>
                
                <div class="form-group">
                    <label>Default Slots</label>
                    <input type="number" id="vaultDefaultSlots" value="9" min="1" max="54">
                </div>
            </div>
            
            <div class="subsection">
                <h3>Permissions & Ranks</h3>
                <div class="form-group">
                    <label>Default Rank - Unlocked Vaults</label>
                    <input type="number" id="vaultDefaultUnlocked" value="1" min="1">
                </div>
                
                <div class="form-group">
                    <label>VIP Rank - Unlocked Vaults</label>
                    <input type="number" id="vaultVipUnlocked" value="4" min="1">
                </div>
                
                <div class="form-group">
                    <label>MVP Rank - Unlocked Vaults</label>
                    <input type="number" id="vaultMvpUnlocked" value="5" min="1">
                </div>
            </div>
        `
    },
    
    afk: {
        title: 'AFK System',
        content: `
            <div class="subsection">
                <h3>Auto AFK Detection</h3>
                <div class="checkbox-group">
                    <input type="checkbox" id="afkAutoEnabled" checked>
                    <label for="afkAutoEnabled">Enable Auto AFK</label>
                </div>
                
                <div class="form-grid">
                    <div class="form-group">
                        <label>AFK After (Seconds)</label>
                        <input type="number" id="afkSeconds" value="300" min="30">
                    </div>
                    
                    <div class="form-group">
                        <label>Check Interval (Seconds)</label>
                        <input type="number" id="afkCheckInterval" value="5" min="1">
                    </div>
                </div>
            </div>
            
            <div class="subsection">
                <h3>AFK Pool (Auto-Teleport)</h3>
                <div class="checkbox-group">
                    <input type="checkbox" id="afkPoolEnabled" checked>
                    <label for="afkPoolEnabled">Enable AFK Pool</label>
                </div>
                
                <div class="form-grid">
                    <div class="form-group">
                        <label>WorldGuard Region Name</label>
                        <input type="text" id="afkRegion" value="afk_pool">
                    </div>
                    
                    <div class="form-group">
                        <label>World Name</label>
                        <input type="text" id="afkWorld" value="world">
                    </div>
                    
                    <div class="form-group">
                        <label>Target Server</label>
                        <input type="text" id="afkServer" value="hub">
                    </div>
                </div>
                
                <div class="checkbox-group">
                    <input type="checkbox" id="afkCrossServer" checked>
                    <label for="afkCrossServer">Cross-Server AFK Pool</label>
                </div>
            </div>
        `
    },
    
    jail: {
        title: 'Jail System',
        content: `
            <div class="subsection">
                <h3>Jail Storage</h3>
                <div class="checkbox-group">
                    <input type="checkbox" id="jailMongo">
                    <label for="jailMongo">Use MongoDB (Cross-server jails)</label>
                </div>
                
                <div class="info-box">
                    <p>Disable for local YAML storage. Enable for cross-server jail sync.</p>
                </div>
                
                <div class="form-grid">
                    <div class="form-group">
                        <label>MongoDB URI</label>
                        <input type="text" id="jailMongoUri" value="mongodb://localhost:27017">
                    </div>
                    
                    <div class="form-group">
                        <label>Database Name</label>
                        <input type="text" id="jailMongoDb" value="oreo">
                    </div>
                </div>
            </div>
        `
    },
    
    economy: {
        title: 'Economy System',
        content: `
            <div class="form-group">
                <label>Economy Type</label>
                <select id="economyType" onchange="toggleEconomyFields()">
                    <option value="none">Disabled</option>
                    <option value="json" selected>JSON (File-based)</option>
                    <option value="mongodb">MongoDB (Cross-server)</option>
                    <option value="postgresql">PostgreSQL (Cross-server)</option>
                </select>
            </div>
            
            <div class="form-grid">
                <div class="form-group">
                    <label>Starting Balance</label>
                    <input type="number" id="startBalance" value="100.0" step="0.01">
                </div>
                
                <div class="form-group">
                    <label>Max Balance</label>
                    <input type="number" id="maxBalance" value="1000000000" step="1">
                </div>
                
                <div class="form-group">
                    <label>Min Balance</label>
                    <input type="number" id="minBalance" value="0.0" step="0.01">
                </div>
                
                <div class="form-group">
                    <label>BalTop Size</label>
                    <input type="number" id="baltopSize" value="10" min="5" max="100">
                </div>
            </div>
            
            <div class="checkbox-group">
                <input type="checkbox" id="allowNegative">
                <label for="allowNegative">Allow Negative Balance</label>
            </div>
            
            <div class="subsection" id="mongoEconomy" style="display:none;">
                <h3>MongoDB Economy Settings</h3>
                <div class="info-box">
                    <p>Need MongoDB? Contact us on Discord for $2/month hosting</p>
                </div>
                
                <div class="form-grid">
                    <div class="form-group full-width">
                        <label>Connection URI</label>
                        <input type="text" id="econMongoUri" value="mongodb://localhost:27017">
                    </div>
                    
                    <div class="form-group">
                        <label>Database Name</label>
                        <input type="text" id="econMongoDb" value="minecraft_economy">
                    </div>
                    
                    <div class="form-group">
                        <label>Collection Name</label>
                        <input type="text" id="econMongoCollection" value="balances">
                    </div>
                </div>
            </div>
            
            <div class="subsection" id="postgresEconomy" style="display:none;">
                <h3>PostgreSQL Economy Settings</h3>
                <div class="form-grid">
                    <div class="form-group full-width">
                        <label>JDBC URL</label>
                        <input type="text" id="postgresUrl" value="jdbc:postgresql://localhost:5432/minecraft_economy">
                    </div>
                    
                    <div class="form-group">
                        <label>Username</label>
                        <input type="text" id="postgresUser" value="postgres">
                    </div>
                    
                    <div class="form-group">
                        <label>Password</label>
                        <input type="password" id="postgresPass" value="postgres211">
                    </div>
                    
                    <div class="form-group">
                        <label>Table Name</label>
                        <input type="text" id="postgresTable" value="balances">
                    </div>
                </div>
            </div>
        `
    },
    
    redis: {
        title: 'Redis Cache (Optional)',
        content: `
            <div class="checkbox-group">
                <input type="checkbox" id="redisEnabled">
                <label for="redisEnabled">Enable Redis Caching</label>
            </div>
            
            <div class="form-grid">
                <div class="form-group">
                    <label>Host</label>
                    <input type="text" id="redisHost" value="localhost">
                </div>
                
                <div class="form-group">
                    <label>Port</label>
                    <input type="number" id="redisPort" value="6379" min="1" max="65535">
                </div>
                
                <div class="form-group full-width">
                    <label>Password (Optional)</label>
                    <input type="password" id="redisPassword" placeholder="Leave blank if no password">
                </div>
                
                <div class="form-group">
                    <label>Cache Expiry (Seconds)</label>
                    <input type="number" id="redisCacheExpiry" value="600" min="60">
                </div>
            </div>
        `
    },
    
    scoreboard: {
        title: 'Scoreboard (Sidebar) - Visual Builder',
        content: `
            <div class="scoreboard-builder">
                <!-- Enable/Disable -->
                <div class="checkbox-group">
                    <input type="checkbox" id="scoreboardEnabled" checked>
                    <label for="scoreboardEnabled">Enable Scoreboard by Default</label>
                </div>
                
                <!-- Update Settings -->
                <div class="form-grid">
                    <div class="form-group">
                        <label>Update Interval (Ticks) <span class="hint">20 ticks = 1 second</span></label>
                        <input type="number" id="scoreboardUpdateTicks" value="10" min="1">
                    </div>
                    
                    <div class="form-group">
                        <label>Title Frame Interval (Ticks)</label>
                        <input type="number" id="scoreboardTitleFrameTicks" value="10" min="1">
                    </div>
                </div>
                
                <!-- Title Builder -->
                <div class="subsection">
                    <h3>Title Builder</h3>
                    <div class="title-builder">
                        <div>
                            <div class="builder-toolbar">
                                <button class="btn btn-small" onclick="addTitleFrame()">+ Add Frame</button>
                                <button class="btn btn-small btn-secondary" onclick="openAnimationModal()">🎬 Add Animation</button>
                                <button class="btn btn-small btn-secondary" onclick="openColorModal()">🎨 Add Color</button>
                            </div>
                            
                            <div id="titleFrames" class="frames-list">
                                <!-- Title frames will be added here dynamically -->
                            </div>
                        </div>
                        
                        <div class="preview-box">
                            <h4>Live Preview</h4>
                            <div id="titlePreview" class="scoreboard-title-preview">
                                &9&lOreo&b&lEssentials
                            </div>
                        </div>
                    </div>
                </div>
                
                <!-- Lines Builder -->
                <div class="subsection">
                    <h3>Lines Builder</h3>
                    <div class="lines-builder">
                        <div>
                            <div class="builder-toolbar">
                                <button class="btn btn-small" onclick="addScoreboardLine()">+ Add Line</button>
                                <button class="btn btn-small btn-secondary" onclick="openPlaceholderModal()">📋 Browse Placeholders</button>
                                <button class="btn btn-small btn-secondary" onclick="openAnimationModal()">🎬 Add Animation</button>
                                <button class="btn btn-small btn-secondary" onclick="openColorModal()">🎨 Add Color</button>
                            </div>
                            
                            <div id="scoreboardLines" class="lines-list">
                                <!-- Lines will be added here dynamically -->
                            </div>
                        </div>
                        
                        <div class="preview-box">
                            <h4>Live Scoreboard Preview</h4>
                            <div id="scoreboardPreview" class="scoreboard-preview">
                                <!-- Preview will be rendered here -->
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            
            <!-- Modals -->
            <div id="placeholderModal" class="modal">
                <div class="modal-content modal-large">
                    <div class="modal-header">
                        <h3>📋 Placeholder Browser</h3>
                        <span class="modal-close" onclick="closePlaceholderModal()">&times;</span>
                    </div>
                    <div class="modal-body">
                        <div class="search-box">
                            <input type="text" id="placeholderSearch" placeholder="Search placeholders..." onkeyup="filterPlaceholders()">
                        </div>
                        
                        <div class="placeholder-tabs">
                            <button class="tab-btn active" onclick="switchPlaceholderTab('oreo')">OreoEssentials</button>
                            <button class="tab-btn" onclick="switchPlaceholderTab('player')">Player</button>
                            <button class="tab-btn" onclick="switchPlaceholderTab('server')">Server</button>
                            <button class="tab-btn" onclick="switchPlaceholderTab('vault')">Vault</button>
                            <button class="tab-btn" onclick="switchPlaceholderTab('luckperms')">LuckPerms</button>
                            <button class="tab-btn" onclick="switchPlaceholderTab('other')">Other</button>
                        </div>
                        
                        <div id="placeholderList" class="placeholder-grid">
                            <!-- Placeholders will be loaded here -->
                        </div>
                    </div>
                </div>
            </div>
            
            <div id="animationModal" class="modal">
                <div class="modal-content">
                    <div class="modal-header">
                        <h3>🎬 Animation Builder</h3>
                        <span class="modal-close" onclick="closeAnimationModal()">&times;</span>
                    </div>
                    <div class="modal-body">
                        <div class="animation-gallery">
                            <div class="animation-card" onclick="selectAnimation('gradient')">
                                <h4>Gradient</h4>
                                <div class="animation-preview gradient-preview">
                                    Animated Text
                                </div>
                                <p>Smooth color transition</p>
                            </div>
                            
                            <div class="animation-card" onclick="selectAnimation('wave')">
                                <h4>Wave</h4>
                                <div class="animation-preview wave-preview">
                                    Wave Effect
                                </div>
                                <p>Wave animation</p>
                            </div>
                            
                            <div class="animation-card" onclick="selectAnimation('flash')">
                                <h4>Flash</h4>
                                <div class="animation-preview flash-preview">
                                    Flash Text
                                </div>
                                <p>Flashing colors</p>
                            </div>
                            
                            <div class="animation-card" onclick="selectAnimation('scroll')">
                                <h4>Scroll</h4>
                                <div class="animation-preview scroll-preview">
                                    Scrolling Text
                                </div>
                                <p>Scrolling effect</p>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            
            <div id="colorModal" class="modal">
                <div class="modal-content">
                    <div class="modal-header">
                        <h3>🎨 Color Picker</h3>
                        <span class="modal-close" onclick="closeColorModal()">&times;</span>
                    </div>
                    <div class="modal-body">
                        <div class="color-sections">
                            <div class="color-section">
                                <h4>Minecraft Colors</h4>
                                <div class="color-grid">
                                    <div class="color-item" onclick="selectColor('&0')" style="background: #000000" title="Black">&0</div>
                                    <div class="color-item" onclick="selectColor('&1')" style="background: #0000AA" title="Dark Blue">&1</div>
                                    <div class="color-item" onclick="selectColor('&2')" style="background: #00AA00" title="Dark Green">&2</div>
                                    <div class="color-item" onclick="selectColor('&3')" style="background: #00AAAA" title="Dark Aqua">&3</div>
                                    <div class="color-item" onclick="selectColor('&4')" style="background: #AA0000" title="Dark Red">&4</div>
                                    <div class="color-item" onclick="selectColor('&5')" style="background: #AA00AA" title="Dark Purple">&5</div>
                                    <div class="color-item" onclick="selectColor('&6')" style="background: #FFAA00" title="Gold">&6</div>
                                    <div class="color-item" onclick="selectColor('&7')" style="background: #AAAAAA" title="Gray">&7</div>
                                    <div class="color-item" onclick="selectColor('&8')" style="background: #555555" title="Dark Gray">&8</div>
                                    <div class="color-item" onclick="selectColor('&9')" style="background: #5555FF" title="Blue">&9</div>
                                    <div class="color-item" onclick="selectColor('&a')" style="background: #55FF55" title="Green">&a</div>
                                    <div class="color-item" onclick="selectColor('&b')" style="background: #55FFFF" title="Aqua">&b</div>
                                    <div class="color-item" onclick="selectColor('&c')" style="background: #FF5555" title="Red">&c</div>
                                    <div class="color-item" onclick="selectColor('&d')" style="background: #FF55FF" title="Light Purple">&d</div>
                                    <div class="color-item" onclick="selectColor('&e')" style="background: #FFFF55" title="Yellow">&e</div>
                                    <div class="color-item" onclick="selectColor('&f')" style="background: #FFFFFF" title="White">&f</div>
                                </div>
                            </div>
                            
                            <div class="color-section">
                                <h4>Hex Color</h4>
                                <div class="hex-picker">
                                    <input type="color" id="hexColorPicker" value="#FF1493">
                                    <button class="btn btn-small" onclick="applyHexColor()">Apply Hex</button>
                                </div>
                            </div>
                            
                            <div class="color-section">
                                <h4>Gradient (MiniMessage)</h4>
                                <div class="gradient-builder">
                                    <input type="color" id="gradientStart" value="#FF1493">
                                    <span>→</span>
                                    <input type="color" id="gradientEnd" value="#00FF7F">
                                    <button class="btn btn-small" onclick="applyGradient()">Apply Gradient</button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `
    },
    
    bossbar: {
        title: 'Bossbar',
        content: `
            <div class="form-group">
                <label>Text (MiniMessage)</label>
                <input type="text" id="bossbarText" value="<gradient:#FF1493:#00FF7F>Welcome</gradient> {player}">
            </div>
            
            <div class="form-grid">
                <div class="form-group">
                    <label>Color</label>
                    <select id="bossbarColor">
                        <option value="WHITE">White</option>
                        <option value="BLUE">Blue</option>
                        <option value="GREEN">Green</option>
                        <option value="PINK">Pink</option>
                        <option value="PURPLE" selected>Purple</option>
                        <option value="RED">Red</option>
                        <option value="YELLOW">Yellow</option>
                    </select>
                </div>
                
                <div class="form-group">
                    <label>Style</label>
                    <select id="bossbarStyle">
                        <option value="SOLID">Solid</option>
                        <option value="SEGMENTED_6">6 Segments</option>
                        <option value="SEGMENTED_10" selected>10 Segments</option>
                        <option value="SEGMENTED_12">12 Segments</option>
                        <option value="SEGMENTED_20">20 Segments</option>
                    </select>
                </div>
                
                <div class="form-group">
                    <label>Progress (0.0 - 1.0)</label>
                    <input type="number" id="bossbarProgress" value="1.0" min="0" max="1" step="0.1">
                </div>
                
                <div class="form-group">
                    <label>Update Interval (Ticks)</label>
                    <input type="number" id="bossbarUpdateTicks" value="40" min="1">
                </div>
            </div>
        `
    },
    
    mobs: {
        title: 'Mob Healthbars',
        content: `
            <div class="form-grid">
                <div class="form-group">
                    <label>Update Interval (Ticks)</label>
                    <input type="number" id="mobUpdateTicks" value="5" min="1">
                </div>
                
                <div class="form-group">
                    <label>View Distance (Blocks)</label>
                    <input type="number" id="mobViewDistance" value="32.0" step="0.5">
                </div>
                
                <div class="form-group">
                    <label>Spawn Cap Per Tick</label>
                    <input type="number" id="mobSpawnCap" value="40" min="1">
                </div>
                
                <div class="form-group">
                    <label>Bar Segments</label>
                    <input type="number" id="mobSegments" value="10" min="5" max="20">
                </div>
            </div>
            
            <div class="subsection">
                <h3>Display Options</h3>
                <div class="checkbox-group">
                    <input type="checkbox" id="mobShowNumbers" checked>
                    <label for="mobShowNumbers">Show Health Numbers</label>
                </div>
                
                <div class="checkbox-group">
                    <input type="checkbox" id="mobIncludePassive" checked>
                    <label for="mobIncludePassive">Include Passive Mobs</label>
                </div>
                
                <div class="checkbox-group">
                    <input type="checkbox" id="mobIncludePlayers">
                    <label for="mobIncludePlayers">Include Players</label>
                </div>
                
                <div class="checkbox-group">
                    <input type="checkbox" id="mobOnlyDamaged">
                    <label for="mobOnlyDamaged">Only Show When Damaged</label>
                </div>
                
                <div class="checkbox-group">
                    <input type="checkbox" id="mobLineOfSight" checked>
                    <label for="mobLineOfSight">Require Line of Sight</label>
                </div>
                
                <div class="checkbox-group">
                    <input type="checkbox" id="mobUseMythicMobs" checked>
                    <label for="mobUseMythicMobs">MythicMobs Integration</label>
                </div>
            </div>
        `
    },
    
    portals: {
        title: 'Portals',
        content: `
            <div class="form-grid">
                <div class="form-group">
                    <label>Cooldown (Milliseconds)</label>
                    <input type="number" id="portalCooldown" value="1000" min="0">
                </div>
                
                <div class="form-group">
                    <label>Sound Effect</label>
                    <input type="text" id="portalSound" value="ENTITY_ENDERMAN_TELEPORT">
                </div>
                
                <div class="form-group">
                    <label>Particle Effect</label>
                    <input type="text" id="portalParticle" value="PORTAL">
                </div>
                
                <div class="form-group">
                    <label>Particle Count</label>
                    <input type="number" id="portalParticleCount" value="20" min="0">
                </div>
                
                <div class="form-group">
                    <label>Max Portal Volume (Blocks)</label>
                    <input type="number" id="portalMaxVolume" value="100000" min="1">
                </div>
            </div>
            
            <div class="checkbox-group">
                <input type="checkbox" id="portalKeepYaw" checked>
                <label for="portalKeepYaw">Allow Keep Yaw/Pitch Feature</label>
            </div>
            
            <div class="checkbox-group">
                <input type="checkbox" id="portalAsync">
                <label for="portalAsync">Async Teleport (Experimental)</label>
            </div>
        `
    },
    
    jumpads: {
        title: 'Jump Pads',
        content: `
            <div class="form-grid">
                <div class="form-group">
                    <label>Default Power (Horizontal)</label>
                    <input type="number" id="jumpPower" value="1.2" step="0.1">
                </div>
                
                <div class="form-group">
                    <label>Default Upward Force</label>
                    <input type="number" id="jumpUpward" value="1.0" step="0.1">
                </div>
                
                <div class="form-group">
                    <label>Cooldown (Milliseconds)</label>
                    <input type="number" id="jumpCooldown" value="800" min="0">
                </div>
                
                <div class="form-group">
                    <label>Sound Effect</label>
                    <input type="text" id="jumpSound" value="ENTITY_FIREWORK_ROCKET_LAUNCH">
                </div>
                
                <div class="form-group">
                    <label>Particle Effect</label>
                    <input type="text" id="jumpParticle" value="CLOUD">
                </div>
                
                <div class="form-group">
                    <label>Particle Count</label>
                    <input type="number" id="jumpParticleCount" value="12" min="0">
                </div>
            </div>
            
            <div class="checkbox-group">
                <input type="checkbox" id="jumpUseLookDir" checked>
                <label for="jumpUseLookDir">Use Look Direction by Default</label>
            </div>
        `
    },
    
    joinquit: {
        title: 'Join & Quit Messages',
        content: `
            <div class="subsection">
                <h3>Join Messages</h3>
                <div class="checkbox-group">
                    <input type="checkbox" id="joinEnable" checked>
                    <label for="joinEnable">Enable Join Messages</label>
                </div>
                
                <div class="checkbox-group">
                    <input type="checkbox" id="joinDisableBackend" checked>
                    <label for="joinDisableBackend">Disable on Backend Servers</label>
                </div>
                
                <div class="checkbox-group">
                    <input type="checkbox" id="joinLookLikePlayer" checked>
                    <label for="joinLookLikePlayer">Look Like Player Message</label>
                </div>
                
                <div class="form-group">
                    <label>Player Name Display</label>
                    <input type="text" id="joinPlayerName" value="<gradient:#9BE8FF:#00D4FF>Oreobot</gradient>">
                </div>
                
                <div class="form-group full-width">
                    <label>First Join Message</label>
                    <textarea id="joinFirstMessage" rows="2"><gradient:#00D4FF:#1E90FF>Welcome</gradient> <gradient:#1E90FF:#9BE8FF>{name}</gradient> <gradient:#9BE8FF:#00D4FF>to the server for the first time!</gradient></textarea>
                </div>
                
                <div class="form-group full-width">
                    <label>Rejoin Message</label>
                    <textarea id="joinRejoinMessage" rows="2"><gradient:#1E90FF:#9BE8FF>{name}</gradient> <gradient:#9BE8FF:#00D4FF>has joined the game.</gradient></textarea>
                </div>
            </div>
            
            <div class="subsection">
                <h3>Quit Messages</h3>
                <div class="checkbox-group">
                    <input type="checkbox" id="quitEnable" checked>
                    <label for="quitEnable">Enable Quit Messages</label>
                </div>
                
                <div class="checkbox-group">
                    <input type="checkbox" id="quitDisableBackend" checked>
                    <label for="quitDisableBackend">Disable on Backend Servers</label>
                </div>
                
                <div class="checkbox-group">
                    <input type="checkbox" id="quitLookLikePlayer" checked>
                    <label for="quitLookLikePlayer">Look Like Player Message</label>
                </div>
                
                <div class="form-group full-width">
                    <label>Quit Message</label>
                    <textarea id="quitMessage" rows="2"><gradient:#1E90FF:#9BE8FF>{name}</gradient> <gradient:#9BE8FF:#00D4FF>has left the game.</gradient></textarea>
                </div>
            </div>
        `
    },
    
    automsg: {
        title: 'Automatic Messages',
        content: `
            <div class="checkbox-group">
                <input type="checkbox" id="autoMsgEnable" checked>
                <label for="autoMsgEnable">Enable Auto Messages</label>
            </div>
            
            <div class="checkbox-group">
                <input type="checkbox" id="autoMsgLookLikePlayer" checked>
                <label for="autoMsgLookLikePlayer">Look Like Player Message</label>
            </div>
            
            <div class="form-group">
                <label>Player Name Display</label>
                <input type="text" id="autoMsgPlayerName" value="<gradient:#9BE8FF:#00D4FF>Oreobot</gradient>">
            </div>
            
            <div class="info-box">
                <p>Add repeating automatic messages that broadcast to all players. Edit full config for multiple messages.</p>
            </div>
        `
    },
    
    
    tab: {
        title: 'Tab List (Player List) - Visual Builder',
        content: `
            <div class="tab-builder">
                <!-- Enable/Disable -->
                <div class="subsection">
                    <h3>General Settings</h3>
                    <div class="checkbox-group">
                        <input type="checkbox" id="tabEnabled" checked>
                        <label for="tabEnabled">Enable Tab List</label>
                    </div>
                    
                    <div class="checkbox-group">
                        <input type="checkbox" id="tabUsePlaceholderAPI" checked>
                        <label for="tabUsePlaceholderAPI">Use PlaceholderAPI</label>
                    </div>
                    
                    <div class="tab-settings-grid">
                        <div class="form-group">
                            <label>Update Interval (Ticks) <span class="hint">20 ticks = 1 second</span></label>
                            <input type="number" id="tabIntervalTicks" value="20" min="1">
                        </div>
                        
                        <div class="form-group">
                            <label>Frame Change Interval (Ticks)</label>
                            <input type="number" id="tabChangeInterval" value="8" min="1">
                        </div>
                        
                        <div class="form-group">
                            <label>Layout Mode</label>
                            <select id="tabLayoutMode">
                                <option value="CUSTOM" selected>Custom Layout</option>
                                <option value="SIMPLE">Simple Layout</option>
                                <option value="COMPACT">Compact Layout</option>
                            </select>
                        </div>
                    </div>
                </div>
                
                <!-- Header Builder -->
                <div class="subsection">
                    <h3>Header Builder (Top Section)</h3>
                    <div class="tab-section-grid">
                        <div>
                            <div class="builder-toolbar">
                                <button class="btn btn-small" onclick="addHeaderFrame()">+ Add Frame</button>
                                <button class="btn btn-small btn-secondary" onclick="openTabPlaceholderModal()">📋 Placeholders</button>
                                <button class="btn btn-small btn-secondary" onclick="openTabAnimationModal('header')">🎬 Animation</button>
                                <button class="btn btn-small btn-secondary" onclick="openTabColorModal('header')">🎨 Color</button>
                            </div>
                            
                            <div id="tabHeaderFrames" class="frames-list" style="max-height: 400px; overflow-y: auto; margin-top: 15px;">
                                <!-- Header frames will be added here -->
                            </div>
                        </div>
                        
                        <div class="tab-preview-container">
                            <h4 style="color: var(--purple-light); margin-bottom: 15px;">Live Header Preview</h4>
                            <div id="tabHeaderPreview" class="tab-header-preview">
                                &9&lOREO&b&lESSENTIALS
                            </div>
                        </div>
                    </div>
                </div>
                
                <!-- Player Section -->
                <div class="subsection">
                    <h3>Player Section (Middle)</h3>
                    
                    <div class="form-group">
                        <label>Player Format Pattern</label>
                        <input type="text" id="tabPlayerFormat" value="%player_color%%player_name%%afk_indicator%" placeholder="%player_color%%player_name%%afk_indicator%">
                        <p class="hint">Variables: %player_color%, %player_name%, %afk_indicator%</p>
                    </div>
                    
                    <div class="form-grid">
                        <div class="checkbox-group">
                            <input type="checkbox" id="tabShowAFK" checked>
                            <label for="tabShowAFK">Show AFK Indicator</label>
                        </div>
                        
                        <div class="form-group">
                            <label>AFK Format</label>
                            <input type="text" id="tabAFKFormat" value=" &7AFK" placeholder=" &7AFK">
                        </div>
                    </div>
                    
                    <h4 style="margin-top: 20px; margin-bottom: 10px;">Rank Colors & Priorities</h4>
                    <div class="builder-toolbar">
                        <button class="btn btn-small" onclick="addRankColor()">+ Add Rank</button>
                    </div>
                    
                    <div id="rankColorsList" style="margin-top: 15px;">
                        <!-- Rank colors will be added here -->
                    </div>
                    
                    <div class="tab-preview-container" style="margin-top: 20px;">
                        <h4 style="color: var(--purple-light); margin-bottom: 15px;">Player List Preview</h4>
                        <div id="tabPlayerPreview" class="tab-player-list-preview">
                            <!-- Player preview will be rendered here -->
                        </div>
                    </div>
                </div>
                
                <!-- Footer Builder -->
                <div class="subsection">
                    <h3>Footer Builder (Bottom Section)</h3>
                    <div class="tab-section-grid">
                        <div>
                            <div class="builder-toolbar">
                                <button class="btn btn-small" onclick="addFooterFrame()">+ Add Frame</button>
                                <button class="btn btn-small btn-secondary" onclick="openTabPlaceholderModal()">📋 Placeholders</button>
                                <button class="btn btn-small btn-secondary" onclick="openTabAnimationModal('footer')">🎬 Animation</button>
                                <button class="btn btn-small btn-secondary" onclick="openTabColorModal('footer')">🎨 Color</button>
                            </div>
                            
                            <div id="tabFooterFrames" class="frames-list" style="max-height: 400px; overflow-y: auto; margin-top: 15px;">
                                <!-- Footer frames will be added here -->
                            </div>
                        </div>
                        
                        <div class="tab-preview-container">
                            <h4 style="color: var(--purple-light); margin-bottom: 15px;">Live Footer Preview</h4>
                            <div id="tabFooterPreview" class="tab-footer-preview">
                                &7Players: &b100 &8| &7Balance: &b$1,234.56
                            </div>
                        </div>
                    </div>
                </div>
                
                <!-- Title Settings -->
                <div class="subsection">
                    <h3>Join Title Settings</h3>
                    <div class="checkbox-group">
                        <input type="checkbox" id="tabTitleEnabled" checked>
                        <label for="tabTitleEnabled">Show Title on Join</label>
                    </div>
                    
                    <div class="form-grid">
                        <div class="form-group">
                            <label>Title Text</label>
                            <input type="text" id="tabTitleText" value="&9&lOREO&b&lESSENTIALS">
                        </div>
                        
                        <div class="form-group">
                            <label>Subtitle Text</label>
                            <input type="text" id="tabTitleSubtitle" value="&7Welcome &b%player_displayname%&7!">
                        </div>
                        
                        <div class="form-group">
                            <label>Fade In (Ticks)</label>
                            <input type="number" id="tabTitleFadeIn" value="10" min="0">
                        </div>
                        
                        <div class="form-group">
                            <label>Stay (Ticks)</label>
                            <input type="number" id="tabTitleStay" value="60" min="1">
                        </div>
                        
                        <div class="form-group">
                            <label>Fade Out (Ticks)</label>
                            <input type="number" id="tabTitleFadeOut" value="10" min="0">
                        </div>
                    </div>
                </div>
                
                <!-- Name Format -->
                <div class="subsection">
                    <h3>Name Format Settings</h3>
                    <div class="checkbox-group">
                        <input type="checkbox" id="tabNameFormatEnabled" checked>
                        <label for="tabNameFormatEnabled">Enable Custom Name Format</label>
                    </div>
                    
                    <div class="form-grid">
                        <div class="form-group">
                            <label>Pattern</label>
                            <input type="text" id="tabNamePattern" value="&f%nick_or_name%%oe_server_tag%">
                        </div>
                        
                        <div class="form-group">
                            <label>Server Tag</label>
                            <input type="text" id="tabServerTag" value=" &8(&9%server_name%&8)">
                        </div>
                        
                        <div class="form-group">
                            <label>Max Length</label>
                            <input type="number" id="tabMaxLength" value="16" min="1" max="40">
                        </div>
                        
                        <div class="form-group">
                            <label>Overflow Handling</label>
                            <select id="tabOverflow">
                                <option value="TRIM" selected>Trim</option>
                                <option value="ELLIPSIS">Ellipsis (...)</option>
                                <option value="WRAP">Wrap</option>
                            </select>
                        </div>
                    </div>
                </div>
                
                <!-- Download Tab.yml Section -->
                <div class="subsection" style="background: linear-gradient(135deg, rgba(139, 92, 246, 0.15), rgba(99, 102, 241, 0.15)); border: 2px solid var(--purple);">
                    <h3 style="color: var(--purple-light);">💾 Download Tab Configuration</h3>
                    <p style="color: var(--text-muted); margin-bottom: 15px;">Download your tab list configuration as a separate tab.yml file</p>
                    <div style="display: flex; gap: 12px; flex-wrap: wrap;">
                        <button class="btn" onclick="previewTabYml()">👁️ Preview tab.yml</button>
                        <button class="btn" onclick="downloadTabYml()">💾 Download tab.yml</button>
                        <button class="btn btn-secondary" onclick="copyTabToClipboard()">📋 Copy to Clipboard</button>
                    </div>
                </div>
            </div>
        `
    },
    
    oreobot: {
        title: 'Oreobot Conversations',
        content: `
            <div class="checkbox-group">
                <input type="checkbox" id="conversationsEnabled" checked>
                <label for="conversationsEnabled">Enable Oreobot Conversations</label>
            </div>
            
            <div class="form-group">
                <label>Custom Call Name</label>
                <input type="text" id="botCallName" value="oreobot">
            </div>
            
            <div class="form-group">
                <label>Player Name Display</label>
                <input type="text" id="botPlayerName" value="<gradient:#9BE8FF:#00D4FF>OREOBOT</gradient>">
            </div>
            
            <div class="form-group full-width">
                <label>Self-Mention Reply</label>
                <textarea id="botSelfMention" rows="2"><gradient:#00D4FF:#1E90FF>Hello</gradient>, <gradient:#1E90FF:#9BE8FF>{name}</gradient><gradient:#9BE8FF:#00D4FF>! How can I assist you today?</gradient></textarea>
            </div>
            
            <div class="info-box">
                <p>Oreobot responds to keywords in chat. Edit full config to add custom questions and responses.</p>
            </div>
        `
    }
};

// Generate all sections
function init() {
    const mainContent = document.getElementById('mainContent');
    
    Object.keys(sections).forEach((key, index) => {
        const section = sections[key];
        const sectionDiv = document.createElement('div');
        sectionDiv.className = 'section' + (index === 0 ? ' active' : '');
        sectionDiv.id = `section-${key}`;
        sectionDiv.innerHTML = `
            <h2>${section.title}</h2>
            ${section.content}
        `;
        mainContent.appendChild(sectionDiv);
    });
    
    // Navigation click handlers
    document.querySelectorAll('.nav-link').forEach(link => {
        link.addEventListener('click', function() {
            const sectionName = this.dataset.section;
            
            // Update active nav
            document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
            this.classList.add('active');
            
            // Update active section
            document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));
            document.getElementById(`section-${sectionName}`).classList.add('active');
            
            // Scroll to top
            window.scrollTo({ top: 0, behavior: 'smooth' });
        });
    });
    
    // Auto-update preview on any input change
    document.addEventListener('input', generateConfig);
    document.addEventListener('change', generateConfig);
    
    // Initial preview generation
    generateConfig();
}

function toggleMongoFields() {
    const storage = document.getElementById('essentialsStorage').value;
    document.getElementById('mongoConfig').style.display = storage === 'mongodb' ? 'block' : 'none';
}

function toggleEconomyFields() {
    const type = document.getElementById('economyType').value;
    document.getElementById('mongoEconomy').style.display = type === 'mongodb' ? 'block' : 'none';
    document.getElementById('postgresEconomy').style.display = type === 'postgresql' ? 'block' : 'none';
}

function generateTabHeaderFrames() {
    const headerInputs = document.querySelectorAll('#tabHeaderFrames textarea');
    if (!headerInputs || headerInputs.length === 0) {
        return `        - "&#00FFFF&m━&#00F4FF&m━&#00E9FF&m━&#00DEFF&m━&#00D3FF&m━\n&9&lOREO&b&lESSENTIALS\n&7Welcome &b%player_displayname%"`;
    }
    
    let frames = [];
    headerInputs.forEach(input => {
        const value = input.value.trim().replace(/\n/g, '\\n');
        if (value) {
            frames.push(`        - "${value}"`);
        }
    });
    
    return frames.length > 0 ? frames.join('\n') : `        - "&9&lOREO&b&lESSENTIALS"`;
}

function generateTabFooterFrames() {
    const footerInputs = document.querySelectorAll('#tabFooterFrames textarea');
    if (!footerInputs || footerInputs.length === 0) {
        return `        - "\n&#00FFFF&m━&#00F4FF&m━&#00E9FF&m━\n&7Players: &b%oe_network_online%\n&9&lOREO&b&lESSENTIALS"`;
    }
    
    let frames = [];
    footerInputs.forEach(input => {
        const value = input.value.trim().replace(/\n/g, '\\n');
        if (value) {
            frames.push(`        - "${value}"`);
        }
    });
    
    return frames.length > 0 ? frames.join('\n') : `        - "&7Server Info"`;
}

function generateTabRankColors() {
    const rankItems = document.querySelectorAll('#rankColorsList .rank-item');
    if (!rankItems || rankItems.length === 0) {
        return `          owner: "&9"
          admin: "&b"
          moderator: "&3"
          helper: "&e"
          vip: "&b"
          default: "&f"`;
    }
    
    let ranks = [];
    rankItems.forEach(item => {
        const rankName = item.querySelector('.rank-name').value.trim();
        const rankColor = item.querySelector('.rank-color').value.trim();
        if (rankName && rankColor) {
            ranks.push(`          ${rankName}: "${rankColor}"`);
        }
    });
    
    return ranks.length > 0 ? ranks.join('\n') : `          default: "&f"`;
}

function generateTabRankPriorities() {
    const rankItems = document.querySelectorAll('#rankColorsList .rank-item');
    if (!rankItems || rankItems.length === 0) {
        return `        owner: 100
        admin: 90
        moderator: 80
        helper: 70
        vip: 60
        default: 10`;
    }
    
    let priorities = [];
    rankItems.forEach(item => {
        const rankName = item.querySelector('.rank-name').value.trim();
        const priority = item.querySelector('.rank-priority').value.trim() || '10';
        if (rankName) {
            priorities.push(`        ${rankName}: ${priority}`);
        }
    });
    
    return priorities.length > 0 ? priorities.join('\n') : `        default: 10`;
}

function generateTabYml() {
    const tabConfig = `# ====================================================================
# TAB LIST CONFIGURATION - OreoEssentials
# Generated by OreoStudios Config Editor
# ====================================================================
tab:
  enabled: ${getValue('tabEnabled', true)}
  use-placeholderapi: ${getValue('tabUsePlaceholderAPI', true)}
  interval-ticks: ${getValue('tabIntervalTicks', 20)}
  layout-mode: "${getValue('tabLayoutMode', 'CUSTOM')}"

  network:
    all-servers: true

  # ====================================================================
  # 🌊 OREOESSENTIALS BLUE GRADIENT FLOWING WAVES! 🌊
  # ====================================================================
  custom-layout:
    enabled: true
    change-interval: ${getValue('tabChangeInterval', 8)}

    # ====================================================================
    # 🌊 ANIMATED FLOWING BLUE WAVE HEADER
    # ====================================================================
    top-section:
      texts:
${generateTabHeaderFrames()}

    # ====================================================================
    # 👥 PLAYER LIST SECTION (Middle)
    # ====================================================================
    player-section:
      enabled: true
      player-format:
        format: "${getValue('tabPlayerFormat', '%player_color%%player_name%%afk_indicator%')}"
        rank-colors:
${generateTabRankColors()}
        show-afk: ${getValue('tabShowAFK', true)}
        afk-format: "${getValue('tabAFKFormat', ' &7AFK')}"
        show-ping-bars: true

    # ====================================================================
    # 🌊 ANIMATED FLOWING BLUE WAVE FOOTER
    # ====================================================================
    bottom-section:
      texts:
${generateTabFooterFrames()}

    # ====================================================================
    # SORTING
    # ====================================================================
    sorting:
      method: "RANK"
      rank-priority:
${generateTabRankPriorities()}

  # ====================================================================
  # JOIN TITLE
  # ====================================================================
  title:
    enabled: ${getValue('tabTitleEnabled', true)}
    show-on-join: true
    text: "${getValue('tabTitleText', '&9&lOREO&b&lESSENTIALS')}"
    subtitle: "${getValue('tabTitleSubtitle', '&7Welcome &b%player_displayname%&7!')}"
    fade-in: ${getValue('tabTitleFadeIn', 10)}
    stay: ${getValue('tabTitleStay', 60)}
    fade-out: ${getValue('tabTitleFadeOut', 10)}

  # ====================================================================
  # NAME FORMAT
  # ====================================================================
  name-format:
    enabled: ${getValue('tabNameFormatEnabled', true)}
    use-rank-formats: true
    rank-key: "%luckperms_primary_group%"
    server-tag: "${getValue('tabServerTag', ' &8(&9%server_name%&8)')}"
    pattern: "${getValue('tabNamePattern', '&f%nick_or_name%%oe_server_tag%')}"
    rank-formats:
${generateTabRankFormats()}
    enforce-max-length: true
    max-length: ${getValue('tabMaxLength', 16)}
    overflow: "${getValue('tabOverflow', 'TRIM')}"
`;
    
    return tabConfig;
}

function downloadTabYml() {
    const tabYml = generateTabYml();
    const blob = new Blob([tabYml], { type: 'text/yaml' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'tab.yml';
    a.click();
    URL.revokeObjectURL(url);
}

function copyTabToClipboard() {
    const tabYml = generateTabYml();
    navigator.clipboard.writeText(tabYml).then(() => {
        alert('tab.yml copied to clipboard!');
    });
}

function previewTabYml() {
    const tabYml = generateTabYml();
    document.getElementById('configPreview').textContent = tabYml;
    // Scroll to preview
    document.getElementById('configPreview').scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function generateTabRankFormats() {
    const rankItems = document.querySelectorAll('#rankColorsList .rank-item');
    if (!rankItems || rankItems.length === 0) {
        return `      default: "&f%nick_or_name%%oe_server_tag%"
      owner: "&9%nick_or_name%%oe_server_tag%"
      admin: "&b%nick_or_name%%oe_server_tag%"`;
    }
    
    const pattern = getValue('tabNamePattern', '&f%nick_or_name%%oe_server_tag%');
    let formats = [];
    
    rankItems.forEach(item => {
        const rankName = item.querySelector('.rank-name').value.trim();
        const rankColor = item.querySelector('.rank-color').value.trim();
        if (rankName && rankColor) {
            const rankPattern = pattern.replace(/&[0-9a-f]/i, rankColor);
            formats.push(`      ${rankName}: "${rankPattern}"`);
        }
    });
    
    return formats.length > 0 ? formats.join('\n') : `      default: "${pattern}"`;
}

function generateConfig() {
    const config = `# ===========================
# OreoEssentials - config.yml
# Generated by OreoStudios Config Editor
# ===========================

features:
  tpa:
    debug: ${getValue('tpaDebug', false)}
    debug-echo-to-player: ${getValue('tpaDebugEcho', false)}

placeholder-debug: ${getValue('placeholderDebug', false)}
tradedebug: ${getValue('tradeDebug', true)}

skins:
  debug: ${getValue('skinsDebug', false)}

server:
  name: "${getValue('serverName', 'survival-1')}"

language: ${getValue('language', 'en')}

# -------- Essentials data store --------
essentials:
  storage: "${getValue('essentialsStorage', 'yaml')}"

storage:
  mongo:
    uri: "${getValue('mongoUri', 'mongodb://localhost:27017')}"
    database: "${getValue('mongoDatabase', 'oreo')}"
    collectionPrefix: "${getValue('mongoPrefix', 'oreo_')}"

network:
  cross-server: ${getValue('crossServer', true)}
  transport: "${getValue('transport', 'plugin_message')}"

invsee:
  allow-edit-while-online-elsewhere: ${getValue('invseeAllowEdit', true)}

playersync:
  inventory: ${getValue('syncInventory', true)}
  xp: ${getValue('syncXp', true)}
  health: ${getValue('syncHealth', true)}
  hunger: ${getValue('syncHunger', true)}

homes:
  maxPerPlayer: ${getValue('maxHomes', 5)}
  permissionBased: ${getValue('permissionBased', true)}

tpa:
  timeoutSeconds: ${getValue('tpaTimeout', 60)}

debug: ${getValue('debugMode', false)}

bossbar:
  text: "${getValue('bossbarText', '<gradient:#FF1493:#00FF7F>Welcome</gradient> {player}')}"
  color: ${getValue('bossbarColor', 'PURPLE')}
  style: ${getValue('bossbarStyle', 'SEGMENTED_10')}
  progress: ${getValue('bossbarProgress', 1.0)}
  update-ticks: ${getValue('bossbarUpdateTicks', 40)}

# -------- Scoreboard (sidebar) --------
# ---------------------------------------------------------------------
# update-ticks: ${getValue('scoreboardUpdateTicks', 10)} means scoreboard refreshes every ${getValue('scoreboardUpdateTicks', 10) / 20} seconds
# This allows you to see the animation frames changing
# ---------------------------------------------------------------------
scoreboard:
  default-enabled: ${getValue('scoreboardEnabled', true)}
  update-ticks: ${getValue('scoreboardUpdateTicks', 10)}
  title:
    frame-ticks: ${getValue('scoreboardTitleFrameTicks', 10)}
    frames:
${generateTitleFrames()}
  worlds:
    whitelist: []
    blacklist: []
  lines:
${generateScoreboardLines()}

Jail:
  Storage:
    Mongo:
      Enabled: ${getValue('jailMongo', false)}
      Uri: "${getValue('jailMongoUri', 'mongodb://localhost:27017')}"
      Database: "${getValue('jailMongoDb', 'oreo')}"

afk:
  auto:
    enabled: ${getValue('afkAutoEnabled', true)}
    seconds: ${getValue('afkSeconds', 300)}
    check-interval-seconds: ${getValue('afkCheckInterval', 5)}

afk-pool:
  enabled: ${getValue('afkPoolEnabled', true)}
  region-name: "${getValue('afkRegion', 'afk_pool')}"
  world-name: "${getValue('afkWorld', 'world')}"
  server: "${getValue('afkServer', 'hub')}"
  cross-server: ${getValue('afkCrossServer', true)}

economy:
  type: "${getValue('economyType', 'json')}"
  baltop-size: ${getValue('baltopSize', 10)}
  starting-balance: ${getValue('startBalance', 100.0)}
  max-balance: ${getValue('maxBalance', 1000000000)}
  min-balance: ${getValue('minBalance', 0.0)}
  allow-negative: ${getValue('allowNegative', false)}
  
  mongodb:
    uri: "${getValue('econMongoUri', 'mongodb://localhost:27017')}"
    database: "${getValue('econMongoDb', 'minecraft_economy')}"
    collection: "${getValue('econMongoCollection', 'balances')}"
  
  postgresql:
    url: "${getValue('postgresUrl', 'jdbc:postgresql://localhost:5432/minecraft_economy')}"
    user: "${getValue('postgresUser', 'postgres')}"
    password: "${getValue('postgresPass', 'postgres211')}"
    table: "${getValue('postgresTable', 'balances')}"

redis:
  enabled: ${getValue('redisEnabled', false)}
  host: "${getValue('redisHost', 'localhost')}"
  port: ${getValue('redisPort', 6379)}
  password: "${getValue('redisPassword', '')}"
  cache-expiry: ${getValue('redisCacheExpiry', 600)}

rabbitmq:
  enabled: ${getValue('rabbitmqEnabled', false)}
  uri: "${getValue('rabbitmqUri', 'amqps://user:pass@host/vhost')}"

portals:
  cooldown_ms: ${getValue('portalCooldown', 1000)}
  sound: "${getValue('portalSound', 'ENTITY_ENDERMAN_TELEPORT')}"
  particle: "${getValue('portalParticle', 'PORTAL')}"
  particle_count: ${getValue('portalParticleCount', 20)}
  allow_keep_yaw_pitch: ${getValue('portalKeepYaw', true)}
  teleport_async: ${getValue('portalAsync', false)}
  max_portal_volume: ${getValue('portalMaxVolume', 100000)}

jumpads:
  default_power: ${getValue('jumpPower', 1.2)}
  default_upward: ${getValue('jumpUpward', 1.0)}
  default_useLookDir: ${getValue('jumpUseLookDir', true)}
  cooldown_ms: ${getValue('jumpCooldown', 800)}
  sound: "${getValue('jumpSound', 'ENTITY_FIREWORK_ROCKET_LAUNCH')}"
  particle: "${getValue('jumpParticle', 'CLOUD')}"
  particle_count: ${getValue('jumpParticleCount', 12)}

playervaults:
  storage: ${getValue('vaultStorage', 'auto')}
  collection: "${getValue('vaultCollection', 'oreo_playervaults')}"
  slots-cap: ${getValue('vaultSlotsCap', 54)}
  default-slots: ${getValue('vaultDefaultSlots', 9)}
  vaults-per-rank:
    global:
      default: ${getValue('vaultDefaultUnlocked', 1)}
      vip: ${getValue('vaultVipUnlocked', 4)}
      mvp: ${getValue('vaultMvpUnlocked', 5)}

mobs:
  healthbar:
    update-interval-ticks: ${getValue('mobUpdateTicks', 5)}
    view-distance: ${getValue('mobViewDistance', 32.0)}
    spawn-per-tick-cap: ${getValue('mobSpawnCap', 40)}
    show-numbers: ${getValue('mobShowNumbers', true)}
    segments: ${getValue('mobSegments', 10)}
    include-passive: ${getValue('mobIncludePassive', true)}
    include-players: ${getValue('mobIncludePlayers', false)}
    only-when-damaged: ${getValue('mobOnlyDamaged', false)}
    require-line-of-sight: ${getValue('mobLineOfSight', true)}
    use-mythicmobs: ${getValue('mobUseMythicMobs', true)}

Join_messages:
  enable: ${getValue('joinEnable', true)}
  disable_on_backend: ${getValue('joinDisableBackend', true)}
  look_like_player: ${getValue('joinLookLikePlayer', true)}
  player_name: "${getValue('joinPlayerName', '<gradient:#9BE8FF:#00D4FF>Oreobot</gradient>')}"
  first_join: "${getValue('joinFirstMessage', '<gradient:#00D4FF:#1E90FF>Welcome</gradient> <gradient:#1E90FF:#9BE8FF>{name}</gradient>')}"
  rejoin_message: "${getValue('joinRejoinMessage', '<gradient:#1E90FF:#9BE8FF>{name}</gradient> <gradient:#9BE8FF:#00D4FF>has joined the game.</gradient>')}"

Quit_messages:
  enable: ${getValue('quitEnable', true)}
  disable_on_backend: ${getValue('quitDisableBackend', true)}
  look_like_player: ${getValue('quitLookLikePlayer', true)}
  message: "${getValue('quitMessage', '<gradient:#1E90FF:#9BE8FF>{name}</gradient> <gradient:#9BE8FF:#00D4FF>has left the game.</gradient>')}"

Automatic_message:
  enable: ${getValue('autoMsgEnable', true)}
  look_like_player: ${getValue('autoMsgLookLikePlayer', true)}
  player_name: "${getValue('autoMsgPlayerName', '<gradient:#9BE8FF:#00D4FF>Oreobot</gradient>')}"

conversations:
  enabled: ${getValue('conversationsEnabled', true)}
  oreobot:
    custom_call_name: "${getValue('botCallName', 'oreobot')}"
    player_name: "${getValue('botPlayerName', '<gradient:#9BE8FF:#00D4FF>OREOBOT</gradient>')}"
    self_mention_reply: "${getValue('botSelfMention', '<gradient:#00D4FF:#1E90FF>Hello</gradient>, <gradient:#1E90FF:#9BE8FF>{name}</gradient>')}"

# ====================================================================
# TAB LIST CONFIGURATION
# ====================================================================
tab:
  enabled: ${getValue('tabEnabled', true)}
  use-placeholderapi: ${getValue('tabUsePlaceholderAPI', true)}
  interval-ticks: ${getValue('tabIntervalTicks', 20)}
  layout-mode: "${getValue('tabLayoutMode', 'CUSTOM')}"

  network:
    all-servers: true

  custom-layout:
    enabled: true
    change-interval: ${getValue('tabChangeInterval', 8)}

    # Header (Top Section) - Animated Frames
    top-section:
      texts:
${generateTabHeaderFrames()}

    # Player Section (Middle)
    player-section:
      enabled: true
      player-format:
        format: "${getValue('tabPlayerFormat', '%player_color%%player_name%%afk_indicator%')}"
        rank-colors:
${generateTabRankColors()}
        show-afk: ${getValue('tabShowAFK', true)}
        afk-format: "${getValue('tabAFKFormat', ' &7AFK')}"
        show-ping-bars: true

    # Footer (Bottom Section) - Animated Frames
    bottom-section:
      texts:
${generateTabFooterFrames()}

    # Sorting
    sorting:
      method: "RANK"
      rank-priority:
${generateTabRankPriorities()}

  # Join Title
  title:
    enabled: ${getValue('tabTitleEnabled', true)}
    show-on-join: true
    text: "${getValue('tabTitleText', '&9&lOREO&b&lESSENTIALS')}"
    subtitle: "${getValue('tabTitleSubtitle', '&7Welcome &b%player_displayname%&7!')}"
    fade-in: ${getValue('tabTitleFadeIn', 10)}
    stay: ${getValue('tabTitleStay', 60)}
    fade-out: ${getValue('tabTitleFadeOut', 10)}

  # Name Format
  name-format:
    enabled: ${getValue('tabNameFormatEnabled', true)}
    use-rank-formats: true
    rank-key: "%luckperms_primary_group%"
    server-tag: "${getValue('tabServerTag', ' &8(&9%server_name%&8)')}"
    pattern: "${getValue('tabNamePattern', '&f%nick_or_name%%oe_server_tag%')}"
    rank-formats:
${generateTabRankFormats()}
    enforce-max-length: true
    max-length: ${getValue('tabMaxLength', 16)}
    overflow: "${getValue('tabOverflow', 'TRIM')}"

# See full documentation for additional features and advanced configuration
`;
    
    document.getElementById('configPreview').textContent = config;
}

function generateTitleFrames() {
    // Get all title frame inputs from the scoreboard builder
    const titleInputs = document.querySelectorAll('#titleFrames input');
    if (!titleInputs || titleInputs.length === 0) {
        // Return default frames if none exist
        return `      - '&9&lOreo&b&lEssentials'
      - '&b&lOreo&9&lEssentials'
      - '&9&lOreo&b&lEssentials'`;
    }
    
    let frames = [];
    titleInputs.forEach(input => {
        const value = input.value.trim();
        if (value) {
            frames.push(`      - '${value}'`);
        }
    });
    
    return frames.length > 0 ? frames.join('\n') : `      - '&9&lOreo&b&lEssentials'`;
}

function generateScoreboardLines() {
    // Get all scoreboard line inputs from the scoreboard builder
    const lineInputs = document.querySelectorAll('#scoreboardLines input, #scoreboardLines textarea');
    if (!lineInputs || lineInputs.length === 0) {
        // Return default lines with full animations
        return `    - |-
      %animations_<tag interval=5>
      <gradient:#0099FF:#00FFFF>━━━━━━━━━━━━━━━━━━━━━━</gradient>
      |
      <gradient:#00CCFF:#0099FF>━━━━━━━━━━━━━━━━━━━━━━</gradient>
      |
      <gradient:#00FFFF:#00CCFF>━━━━━━━━━━━━━━━━━━━━━━</gradient>
      |
      <gradient:#00CCFF:#00FFFF>━━━━━━━━━━━━━━━━━━━━━━</gradient>
      |
      <gradient:#0099FF:#00CCFF>━━━━━━━━━━━━━━━━━━━━━━</gradient>
      </tag>%
    - ''
    - '&7Player: &b{player}'
    - '&7Rank: %luckperms_prefix%%luckperms_primary_group_name%'
    - ''
    - |-
      %animations_<tag interval=10>
      <gradient:#FFD700:#FFA500>Balance:</gradient> &e%vault_eco_balance_formatted%
      |
      <gradient:#FFA500:#FFD700>Balance:</gradient> &6%vault_eco_balance_formatted%
      |
      <gradient:#FFFF00:#FFD700>Balance:</gradient> &e%vault_eco_balance_formatted%
      |
      <gradient:#FFD700:#FFFF00>Balance:</gradient> &6%vault_eco_balance_formatted%
      </tag>%
    - ''
    - '&7Online: &a%oreo_network_online%&7/&a%server_max_players%'
    - ''
    - |-
      %animations_<tag interval=5>
      <gradient:#0099FF:#00FFFF>━━━━━━━━━━━━━━━━━━━━━━</gradient>
      |
      <gradient:#00CCFF:#0099FF>━━━━━━━━━━━━━━━━━━━━━━</gradient>
      |
      <gradient:#00FFFF:#00CCFF>━━━━━━━━━━━━━━━━━━━━━━</gradient>
      |
      <gradient:#00CCFF:#00FFFF>━━━━━━━━━━━━━━━━━━━━━━</gradient>
      |
      <gradient:#0099FF:#00CCFF>━━━━━━━━━━━━━━━━━━━━━━</gradient>
      </tag>%
    - '&7&oplay.example.com'`;
    }
    
    let lines = [];
    lineInputs.forEach(input => {
        const value = input.value.trim();
        
        // Check if this line contains animation tags or multiple frames (indicated by |)
        if (value.includes('%animations_') || value.includes('<tag interval=')) {
            // Multi-line format with |-
            // Split by | and trim each part
            const parts = value.split('|').map(p => p.trim()).filter(p => p);
            
            if (parts.length > 1 || value.includes('%animations_')) {
                lines.push(`    - |-`);
                if (value.includes('%animations_')) {
                    // Keep animation wrapper intact
                    lines.push(`      ${value}`);
                } else {
                    // Split into multiple lines
                    parts.forEach(part => {
                        lines.push(`      ${part}`);
                    });
                }
            } else {
                lines.push(`    - '${value}'`);
            }
        } else if (value === '') {
            // Empty line
            lines.push(`    - ''`);
        } else {
            // Simple line
            lines.push(`    - '${value}'`);
        }
    });
    
    return lines.length > 0 ? lines.join('\n') : `    - ''`;
}

function getValue(id, defaultValue) {
    const el = document.getElementById(id);
    if (!el) return defaultValue;
    
    if (el.type === 'checkbox') {
        return el.checked;
    } else if (el.type === 'number') {
        return parseFloat(el.value) || defaultValue;
    }
    return el.value || defaultValue;
}

function downloadConfig() {
    const configText = document.getElementById('configPreview').textContent;
    const blob = new Blob([configText], { type: 'text/yaml' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'config.yml';
    a.click();
    URL.revokeObjectURL(url);
}

function copyToClipboard() {
    const configText = document.getElementById('configPreview').textContent;
    navigator.clipboard.writeText(configText).then(() => {
        alert('Config copied to clipboard!');
    });
}

// Initialize on load
document.addEventListener('DOMContentLoaded', init);