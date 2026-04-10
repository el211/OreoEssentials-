// Advanced Scoreboard Section for OreoEssentials Config Editor

const scoreboardSection = {
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
                    <div class="builder-toolbar">
                        <button class="btn btn-small" onclick="addTitleFrame()">+ Add Frame</button>
                        <button class="btn btn-small btn-secondary" onclick="openAnimationPicker('title')">🎬 Add Animation</button>
                        <button class="btn btn-small btn-secondary" onclick="openColorPicker('title')">🎨 Add Color</button>
                    </div>
                    
                    <div id="titleFrames" class="frames-list">
                        <!-- Title frames will be added here dynamically -->
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
                    <div class="builder-toolbar">
                        <button class="btn btn-small" onclick="addScoreboardLine()">+ Add Line</button>
                        <button class="btn btn-small btn-secondary" onclick="openPlaceholderBrowser()">📋 Browse Placeholders</button>
                        <button class="btn btn-small btn-secondary" onclick="openAnimationPicker('line')">🎬 Add Animation</button>
                        <button class="btn btn-small btn-secondary" onclick="openColorPicker('line')">🎨 Add Color</button>
                    </div>
                    
                    <div id="scoreboardLines" class="lines-list">
                        <!-- Lines will be added here dynamically -->
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
                                <gradient:#FF1493:#00FF7F>Animated Text</gradient>
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
};

// Placeholder database - COMPLETE OreoEssentials placeholders
const placeholders = {
    oreo: {
        name: 'OreoEssentials',
        items: [
            // Economy
            { placeholder: '%oreo_balance%', description: 'Player balance (raw number)' },
            { placeholder: '%oreo_balance_formatted%', description: 'Formatted balance (e.g., 1,234.56)' },
            
            // Server & World
            { placeholder: '%oreo_server_name%', description: 'Server name from config' },
            { placeholder: '%oreo_server_nick%', description: 'Server nickname for current server' },
            { placeholder: '%oreo_world_name%', description: 'Current world name (e.g., world)' },
            { placeholder: '%oreo_world_nick%', description: 'World nickname (e.g., OVERWORLD)' },
            { placeholder: '%oreo_network_online%', description: 'Online players count across network' },
            
            // Homes
            { placeholder: '%oreo_homes_used%', description: 'Number of homes used' },
            { placeholder: '%oreo_homes_max%', description: 'Maximum homes allowed' },
            { placeholder: '%oreo_homes%', description: 'Homes in format: used/max' },
            
            // Player Vaults
            { placeholder: '%oreo_vaults_enabled%', description: 'Vaults enabled (true/false)' },
            { placeholder: '%oreo_vaults_max%', description: 'Maximum vaults available' },
            { placeholder: '%oreo_vaults_unlocked_count%', description: 'Number of unlocked vaults' },
            { placeholder: '%oreo_vaults_unlocked_list%', description: 'List of unlocked vault IDs' },
            { placeholder: '%oreo_vaults_locked_list%', description: 'List of locked vault IDs' },
            { placeholder: '%oreo_vault_can_access_1%', description: 'Can access vault #1 (true/false)' },
            { placeholder: '%oreo_vault_slots_1%', description: 'Number of slots in vault #1' },
            { placeholder: '%oreo_vault_rows_1%', description: 'Number of rows in vault #1' },
            { placeholder: '%oreo_vault_title_preview_1%', description: 'Title preview for vault #1' },
            
            // Kits
            { placeholder: '%oreo_kits_enabled%', description: 'Kits enabled (true/false)' },
            { placeholder: '%oreo_kits_count%', description: 'Total available kits' },
            { placeholder: '%oreo_kits_ready_count%', description: 'Kits ready to claim' },
            { placeholder: '%oreo_kits_ready_list%', description: 'List of ready kit names' },
            { placeholder: '%oreo_kit_ready_tools%', description: 'Kit "tools" ready (true/false)' },
            { placeholder: '%oreo_kit_cooldown_tools%', description: 'Cooldown for "tools" kit (seconds or "ready")' },
            { placeholder: '%oreo_kit_cooldown_formatted_tools%', description: 'Formatted cooldown (e.g., 5m 30s)' },
            
            // Playtime & Rewards
            { placeholder: '%oreo_playtime_total_seconds%', description: 'Total playtime in seconds' },
            { placeholder: '%oreo_prewards_enabled%', description: 'Playtime rewards enabled' },
            { placeholder: '%oreo_prewards_ready_count%', description: 'Ready playtime rewards' },
            { placeholder: '%oreo_prewards_ready_list%', description: 'List of ready reward names' },
            { placeholder: '%oreo_prewards_state_reward1%', description: 'State of reward (LOCKED/READY/CLAIMED)' }
        ]
    },
    player: {
        name: 'Player Info',
        items: [
            { placeholder: '%player_name%', description: 'Player name' },
            { placeholder: '%player_displayname%', description: 'Player display name' },
            { placeholder: '%player_health%', description: 'Player health' },
            { placeholder: '%player_health_rounded%', description: 'Rounded health' },
            { placeholder: '%player_level%', description: 'Player XP level' },
            { placeholder: '%player_exp%', description: 'Player experience' },
            { placeholder: '%player_food_level%', description: 'Hunger level' },
            { placeholder: '%player_ping%', description: 'Player ping' },
            { placeholder: '%player_world%', description: 'Player world' },
            { placeholder: '%player_x%', description: 'X coordinate' },
            { placeholder: '%player_y%', description: 'Y coordinate' },
            { placeholder: '%player_z%', description: 'Z coordinate' }
        ]
    },
    server: {
        name: 'Server Info',
        items: [
            { placeholder: '%server_name%', description: 'Server name' },
            { placeholder: '%server_online%', description: 'Online players' },
            { placeholder: '%server_max_players%', description: 'Max players' },
            { placeholder: '%server_tps%', description: 'Server TPS' },
            { placeholder: '%server_ram_used%', description: 'RAM used' },
            { placeholder: '%server_ram_free%', description: 'RAM free' },
            { placeholder: '%server_time_<format>%', description: 'Server time' }
        ]
    },
    vault: {
        name: 'Vault Economy',
        items: [
            { placeholder: '%vault_eco_balance%', description: 'Vault balance' },
            { placeholder: '%vault_eco_balance_formatted%', description: 'Formatted balance' },
            { placeholder: '%vault_prefix%', description: 'Vault prefix' },
            { placeholder: '%vault_suffix%', description: 'Vault suffix' },
            { placeholder: '%vault_group%', description: 'Player group' }
        ]
    },
    luckperms: {
        name: 'LuckPerms',
        items: [
            { placeholder: '%luckperms_prefix%', description: 'LuckPerms prefix' },
            { placeholder: '%luckperms_suffix%', description: 'LuckPerms suffix' },
            { placeholder: '%luckperms_meta_<key>%', description: 'Meta value' },
            { placeholder: '%luckperms_primary_group_name%', description: 'Primary group' },
            { placeholder: '%luckperms_groups%', description: 'All groups' }
        ]
    },
    other: {
        name: 'Other Plugins',
        items: [
            { placeholder: '%animations_<tag>text</tag>%', description: 'Animated text' },
            { placeholder: '%server_tps_1%', description: '1 min TPS' },
            { placeholder: '%server_tps_5%', description: '5 min TPS' },
            { placeholder: '%server_tps_15%', description: '15 min TPS' }
        ]
    }
};

// Initialize scoreboard builder
function initScoreboardBuilder() {
    // Add default title frames
    addTitleFrame('&9&lOreo&b&lEssentials');
    addTitleFrame('&b&lOreo&9&lEssentials');
    addTitleFrame('&9&lOreo&b&lEssentials');
    
    // Add default lines with animations
    addScoreboardLine(`%animations_<tag interval=5>
<gradient:#0099FF:#00FFFF>━━━━━━━━━━━━━━━━━━━━━━</gradient>
|
<gradient:#00CCFF:#0099FF>━━━━━━━━━━━━━━━━━━━━━━</gradient>
|
<gradient:#00FFFF:#00CCFF>━━━━━━━━━━━━━━━━━━━━━━</gradient>
|
<gradient:#00CCFF:#00FFFF>━━━━━━━━━━━━━━━━━━━━━━</gradient>
|
<gradient:#0099FF:#00CCFF>━━━━━━━━━━━━━━━━━━━━━━</gradient>
</tag>%`);
    addScoreboardLine('');
    addScoreboardLine('&7Player: &b{player}');
    addScoreboardLine('&7Rank: %luckperms_prefix%%luckperms_primary_group_name%');
    addScoreboardLine('');
    addScoreboardLine(`%animations_<tag interval=10>
<gradient:#FFD700:#FFA500>Balance:</gradient> &e%vault_eco_balance_formatted%
|
<gradient:#FFA500:#FFD700>Balance:</gradient> &6%vault_eco_balance_formatted%
|
<gradient:#FFFF00:#FFD700>Balance:</gradient> &e%vault_eco_balance_formatted%
|
<gradient:#FFD700:#FFFF00>Balance:</gradient> &6%vault_eco_balance_formatted%
</tag>%`);
    addScoreboardLine('');
    addScoreboardLine('&7Online: &a%oreo_network_online%&7/&a%server_max_players%');
    addScoreboardLine('');
    addScoreboardLine(`%animations_<tag interval=5>
<gradient:#0099FF:#00FFFF>━━━━━━━━━━━━━━━━━━━━━━</gradient>
|
<gradient:#00CCFF:#0099FF>━━━━━━━━━━━━━━━━━━━━━━</gradient>
|
<gradient:#00FFFF:#00CCFF>━━━━━━━━━━━━━━━━━━━━━━</gradient>
|
<gradient:#00CCFF:#00FFFF>━━━━━━━━━━━━━━━━━━━━━━</gradient>
|
<gradient:#0099FF:#00CCFF>━━━━━━━━━━━━━━━━━━━━━━</gradient>
</tag>%`);
    addScoreboardLine('&7&oplay.example.com');
    
    updateScoreboardPreview();
    startAnimationLoop();
}

let titleFrameCount = 0;
function addTitleFrame(text = '') {
    titleFrameCount++;
    const container = document.getElementById('titleFrames');
    const frameDiv = document.createElement('div');
    frameDiv.className = 'frame-item';
    frameDiv.id = `titleFrame${titleFrameCount}`;
    frameDiv.innerHTML = `
        <span class="frame-number">#${titleFrameCount}</span>
        <input type="text" value="${text}" placeholder="Title frame text..." onchange="updateScoreboardPreview()">
        <button class="btn-small btn-danger" onclick="removeTitleFrame(${titleFrameCount})">✕</button>
    `;
    container.appendChild(frameDiv);
    updateScoreboardPreview();
}

function removeTitleFrame(id) {
    const frame = document.getElementById(`titleFrame${id}`);
    if (frame) frame.remove();
    updateScoreboardPreview();
}

let lineCount = 0;
function addScoreboardLine(text = '') {
    lineCount++;
    const container = document.getElementById('scoreboardLines');
    const lineDiv = document.createElement('div');
    lineDiv.className = 'line-item';
    lineDiv.id = `line${lineCount}`;
    
    // Check if this is a multi-line animation (contains | or animation tags)
    const isMultiline = text.includes('|') || text.includes('%animations_') || text.includes('<tag interval=');
    
    if (isMultiline) {
        lineDiv.innerHTML = `
            <span class="line-number">${lineCount}</span>
            <textarea rows="4" placeholder="Animation or multi-line text..." onchange="updateScoreboardPreview()">${text}</textarea>
            <button class="btn-small btn-danger" onclick="removeScoreboardLine(${lineCount})">✕</button>
        `;
    } else {
        lineDiv.innerHTML = `
            <span class="line-number">${lineCount}</span>
            <input type="text" value="${text}" placeholder="Scoreboard line..." onchange="updateScoreboardPreview()">
            <button class="btn-small btn-danger" onclick="removeScoreboardLine(${lineCount})">✕</button>
        `;
    }
    
    container.appendChild(lineDiv);
    updateScoreboardPreview();
}

function removeScoreboardLine(id) {
    const line = document.getElementById(`line${id}`);
    if (line) line.remove();
    updateScoreboardPreview();
}

let currentTarget = null;
let currentTargetType = null;

function openPlaceholderModal() {
    document.getElementById('placeholderModal').style.display = 'flex';
    loadPlaceholders('oreo');
}

function closePlaceholderModal() {
    document.getElementById('placeholderModal').style.display = 'none';
}

function openAnimationModal() {
    document.getElementById('animationModal').style.display = 'flex';
}

function closeAnimationModal() {
    document.getElementById('animationModal').style.display = 'none';
}

function openColorModal() {
    document.getElementById('colorModal').style.display = 'flex';
}

function closeColorModal() {
    document.getElementById('colorModal').style.display = 'none';
}

function switchPlaceholderTab(tab) {
    document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
    event.target.classList.add('active');
    loadPlaceholders(tab);
}

function loadPlaceholders(category) {
    const container = document.getElementById('placeholderList');
    const data = placeholders[category];
    
    container.innerHTML = '';
    data.items.forEach(item => {
        const div = document.createElement('div');
        div.className = 'placeholder-item';
        div.innerHTML = `
            <div class="placeholder-code">${item.placeholder}</div>
            <div class="placeholder-desc">${item.description}</div>
            <button class="btn btn-small" onclick="insertPlaceholder('${item.placeholder}')">Insert</button>
        `;
        container.appendChild(div);
    });
}

function insertPlaceholder(placeholder) {
    // Insert at cursor position in active input
    const activeInput = document.activeElement;
    if (activeInput && activeInput.tagName === 'INPUT') {
        const start = activeInput.selectionStart;
        const end = activeInput.selectionEnd;
        const text = activeInput.value;
        activeInput.value = text.substring(0, start) + placeholder + text.substring(end);
        activeInput.focus();
    }
    closePlaceholderModal();
    updateScoreboardPreview();
}

function selectColor(color) {
    const activeInput = document.activeElement;
    if (activeInput && activeInput.tagName === 'INPUT') {
        const start = activeInput.selectionStart;
        const text = activeInput.value;
        activeInput.value = text.substring(0, start) + color + text.substring(start);
        activeInput.focus();
    }
    closeColorModal();
    updateScoreboardPreview();
}

function applyHexColor() {
    const hex = document.getElementById('hexColorPicker').value;
    const color = `<${hex}>`;
    const activeInput = document.activeElement;
    if (activeInput && activeInput.tagName === 'INPUT') {
        activeInput.value += color;
    }
    updateScoreboardPreview();
}

function applyGradient() {
    const start = document.getElementById('gradientStart').value;
    const end = document.getElementById('gradientEnd').value;
    const gradient = `<gradient:${start}:${end}>Text here</gradient>`;
    const activeInput = document.activeElement;
    if (activeInput && activeInput.tagName === 'INPUT') {
        activeInput.value += gradient;
    }
    updateScoreboardPreview();
}

function selectAnimation(type) {
    let animation = '';
    switch(type) {
        case 'gradient':
            animation = `%animations_<tag interval=5>
<gradient:#0099FF:#00FFFF>━━━━━━━━━━━━━━━━━━━━━━</gradient>
|
<gradient:#00CCFF:#0099FF>━━━━━━━━━━━━━━━━━━━━━━</gradient>
|
<gradient:#00FFFF:#00CCFF>━━━━━━━━━━━━━━━━━━━━━━</gradient>
|
<gradient:#00CCFF:#00FFFF>━━━━━━━━━━━━━━━━━━━━━━</gradient>
|
<gradient:#0099FF:#00CCFF>━━━━━━━━━━━━━━━━━━━━━━</gradient>
</tag>%`;
            break;
        case 'wave':
            animation = `%animations_<tag interval=10>
<gradient:#FFD700:#FFA500>Balance:</gradient> &e%vault_eco_balance_formatted%
|
<gradient:#FFA500:#FFD700>Balance:</gradient> &6%vault_eco_balance_formatted%
|
<gradient:#FFFF00:#FFD700>Balance:</gradient> &e%vault_eco_balance_formatted%
|
<gradient:#FFD700:#FFFF00>Balance:</gradient> &6%vault_eco_balance_formatted%
</tag>%`;
            break;
        case 'flash':
            animation = `%animations_<tag interval=5>&a■ &aOnline&e■ &eOnline&c■ &cOnline</tag>%`;
            break;
        case 'scroll':
            animation = `%animations_<tag interval=2>→  Scrolling Text  →|  → Scrolling Text →  </tag>%`;
            break;
    }
    
    const activeInput = document.activeElement;
    if (activeInput && (activeInput.tagName === 'INPUT' || activeInput.tagName === 'TEXTAREA')) {
        // For animations, add a new line if needed
        if (type === 'gradient' || type === 'wave') {
            // These are multi-line, so add as a new line
            addScoreboardLine(animation);
        } else {
            // These are single-line, can be added to existing input
            const start = activeInput.selectionStart || 0;
            const end = activeInput.selectionEnd || 0;
            const value = activeInput.value;
            activeInput.value = value.substring(0, start) + animation + value.substring(end);
        }
    }
    closeAnimationModal();
    updateScoreboardPreview();
}

function updateScoreboardPreview() {
    // Update title preview
    const titleFrames = document.querySelectorAll('#titleFrames input');
    if (titleFrames.length > 0) {
        const titleText = titleFrames[0].value;
        document.getElementById('titlePreview').innerHTML = parseMinecraftColors(titleText);
    }
    
    // Update scoreboard preview
    const preview = document.getElementById('scoreboardPreview');
    const lines = document.querySelectorAll('#scoreboardLines input');
    
    let html = '<div class="scoreboard-container">';
    html += `<div class="scoreboard-title">${parseMinecraftColors(titleFrames[0]?.value || 'Scoreboard')}</div>`;
    
    lines.forEach((line, index) => {
        html += `<div class="scoreboard-line">${parseMinecraftColors(line.value)}</div>`;
    });
    
    html += '</div>';
    preview.innerHTML = html;
}

function parseMinecraftColors(text) {
    // Parse color codes
    text = text.replace(/&([0-9a-fk-or])/g, '<span class="mc-$1">');
    text = text.replace(/<gradient:(#[0-9A-Fa-f]{6}):(#[0-9A-Fa-f]{6})>(.*?)<\/gradient>/g, 
        '<span class="gradient" style="background: linear-gradient(90deg, $1, $2); -webkit-background-clip: text; color: transparent;">$3</span>');
    
    // Replace placeholders with examples
    text = text.replace(/%player_name%/g, 'Steve');
    text = text.replace(/%oreo_balance_formatted%/g, '$1,234.56');
    text = text.replace(/%oreo_homes%/g, '3/5');
    text = text.replace(/%server_online%/g, '42');
    
    return text;
}

function startAnimationLoop() {
    let frameIndex = 0;
    setInterval(() => {
        const titleFrames = document.querySelectorAll('#titleFrames input');
        if (titleFrames.length > 0) {
            frameIndex = (frameIndex + 1) % titleFrames.length;
            const titleText = titleFrames[frameIndex].value;
            document.getElementById('titlePreview').innerHTML = parseMinecraftColors(titleText);
        }
    }, 1000);
}

function filterPlaceholders() {
    const search = document.getElementById('placeholderSearch').value.toLowerCase();
    document.querySelectorAll('.placeholder-item').forEach(item => {
        const text = item.textContent.toLowerCase();
        item.style.display = text.includes(search) ? 'flex' : 'none';
    });
}

// Initialize when section loads (optimized)
let scoreboardBuilderInitialized = false;

document.addEventListener('DOMContentLoaded', () => {
    // Listen for scoreboard section click
    const scoreboardNavLink = document.querySelector('[data-section="scoreboard"]');
    if (scoreboardNavLink) {
        scoreboardNavLink.addEventListener('click', () => {
            // Small delay to ensure DOM is ready
            setTimeout(() => {
                if (!scoreboardBuilderInitialized && document.getElementById('titleFrames')) {
                    initScoreboardBuilder();
                    scoreboardBuilderInitialized = true;
                }
            }, 50); // Reduced delay
        });
    }
});