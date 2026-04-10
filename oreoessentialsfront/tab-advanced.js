// Tab List Advanced Builder - Complete JavaScript
// Supports header/footer animations, player formats, rank colors, and sorting

let headerFrameCount = 0;
let footerFrameCount = 0;
let currentHeaderFrame = 0;
let currentFooterFrame = 0;

// Initialize tab builder
function initTabBuilder() {
    // Add default header frames (3 simple gradient frames - NO STRIKETHROUGH)
    addHeaderFrame(`<gradient:#00FFFF:#0000FF>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>
&9&lOREO&b&lESSENTIALS
&7Welcome &b%player_displayname% &8| &7Ping: &b%player_ping%ms`);
    
    addHeaderFrame(`<gradient:#00F4FF:#000BFF>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>
&9&lOREO&b&lESSENTIALS
&7Welcome &b%player_displayname% &8| &7Ping: &b%player_ping%ms`);
    
    addHeaderFrame(`<gradient:#00E9FF:#0016FF>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>
&9&lOREO&b&lESSENTIALS
&7Welcome &b%player_displayname% &8| &7Ping: &b%player_ping%ms`);
    
    // Add default footer frames (3 simple gradient frames)
    addFooterFrame(`
<gradient:#00FFFF:#0000FF>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>
&7Players: &b%oe_network_online% &8| &7Balance: &b$%vault_eco_balance_formatted%
&9&lOREO&b&lESSENTIALS &7- &fYourServer.com`);
    
    addFooterFrame(`
<gradient:#00F4FF:#000BFF>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>
&7Players: &b%oe_network_online% &8| &7Balance: &b$%vault_eco_balance_formatted%
&9&lOREO&b&lESSENTIALS &7- &fYourServer.com`);
    
    addFooterFrame(`
<gradient:#00E9FF:#0016FF>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>
&7Players: &b%oe_network_online% &8| &7Balance: &b$%vault_eco_balance_formatted%
&9&lOREO&b&lESSENTIALS &7- &fYourServer.com`);
    
    // Add default rank colors
    addRankColor('owner', '&9', 100);
    addRankColor('admin', '&b', 90);
    addRankColor('moderator', '&3', 80);
    addRankColor('helper', '&e', 70);
    addRankColor('vip', '&b', 60);
    addRankColor('default', '&f', 10);
    
    updateTabPreview();
    startTabAnimationLoop();
}

// Header frame functions
function addHeaderFrame(text = '') {
    headerFrameCount++;
    const container = document.getElementById('tabHeaderFrames');
    const frameDiv = document.createElement('div');
    frameDiv.className = 'frame-item';
    frameDiv.id = `headerFrame${headerFrameCount}`;
    frameDiv.innerHTML = `
        <span class="frame-number">#${headerFrameCount}</span>
        <textarea rows="3" placeholder="Header frame (supports \\n for new lines)..." onchange="updateTabPreview()">${text}</textarea>
        <button class="btn-small btn-danger" onclick="removeHeaderFrame(${headerFrameCount})">✕</button>
    `;
    container.appendChild(frameDiv);
    updateTabPreview();
}

function removeHeaderFrame(id) {
    const frame = document.getElementById(`headerFrame${id}`);
    if (frame) frame.remove();
    updateTabPreview();
}

// Footer frame functions
function addFooterFrame(text = '') {
    footerFrameCount++;
    const container = document.getElementById('tabFooterFrames');
    const frameDiv = document.createElement('div');
    frameDiv.className = 'frame-item';
    frameDiv.id = `footerFrame${footerFrameCount}`;
    frameDiv.innerHTML = `
        <span class="frame-number">#${footerFrameCount}</span>
        <textarea rows="3" placeholder="Footer frame (supports \\n for new lines)..." onchange="updateTabPreview()">${text}</textarea>
        <button class="btn-small btn-danger" onclick="removeFooterFrame(${footerFrameCount})">✕</button>
    `;
    container.appendChild(frameDiv);
    updateTabPreview();
}

function removeFooterFrame(id) {
    const frame = document.getElementById(`footerFrame${id}`);
    if (frame) frame.remove();
    updateTabPreview();
}

// Rank color functions
function addRankColor(rank = '', color = '&f', priority = 10) {
    const container = document.getElementById('rankColorsList');
    const rankDiv = document.createElement('div');
    rankDiv.className = 'rank-item';
    rankDiv.innerHTML = `
        <input type="text" class="rank-name" value="${rank}" placeholder="Rank name..." onchange="updateTabPreview()">
        <input type="text" class="rank-color" value="${color}" placeholder="&a" onchange="updateTabPreview()">
        <input type="number" class="rank-priority" value="${priority}" placeholder="Priority" onchange="updateTabPreview()">
        <button class="btn-small btn-danger" onclick="this.parentElement.remove(); updateTabPreview()">✕</button>
    `;
    container.appendChild(rankDiv);
    updateTabPreview();
}

// Modal functions (reuse from scoreboard)
function openTabPlaceholderModal() {
    openPlaceholderModal(); // Reuse scoreboard modal
}

function openTabAnimationModal(context) {
    openAnimationModal(); // Reuse scoreboard modal
}

function openTabColorModal(context) {
    openColorModal(); // Reuse scoreboard modal
}

function closeTabPlaceholderModal() {
    closePlaceholderModal();
}

function closeTabAnimationModal() {
    closeAnimationModal();
}

function closeTabColorModal() {
    closeColorModal();
}

// Insert functions
function insertTabPlaceholder(placeholder) {
    const activeInput = document.activeElement;
    if (activeInput && (activeInput.tagName === 'INPUT' || activeInput.tagName === 'TEXTAREA')) {
        const start = activeInput.selectionStart || 0;
        const end = activeInput.selectionEnd || 0;
        const value = activeInput.value;
        activeInput.value = value.substring(0, start) + placeholder + value.substring(end);
        activeInput.setSelectionRange(start + placeholder.length, start + placeholder.length);
        activeInput.focus();
    }
    closePlaceholderModal();
    updateTabPreview();
}

// Preview and animation
function updateTabPreview() {
    const headerInputs = document.querySelectorAll('#tabHeaderFrames textarea');
    const footerInputs = document.querySelectorAll('#tabFooterFrames textarea');
    
    if (headerInputs.length > 0) {
        const headerText = headerInputs[currentHeaderFrame % headerInputs.length].value;
        document.getElementById('tabHeaderPreview').innerHTML = parseMinecraftColors(headerText);
    }
    
    if (footerInputs.length > 0) {
        const footerText = footerInputs[currentFooterFrame % footerInputs.length].value;
        document.getElementById('tabFooterPreview').innerHTML = parseMinecraftColors(footerText);
    }
    
    // Update player list preview
    const rankItems = document.querySelectorAll('#rankColorsList .rank-item');
    let playerList = '';
    rankItems.forEach(item => {
        const rankName = item.querySelector('.rank-name').value;
        const rankColor = item.querySelector('.rank-color').value;
        if (rankName) {
            playerList += `<div class="tab-player">${parseMinecraftColors(rankColor + rankName.toUpperCase())}</div>`;
        }
    });
    document.getElementById('tabPlayerPreview').innerHTML = playerList || '<div class="tab-player">&fNo ranks defined</div>';
}

function startTabAnimationLoop() {
    setInterval(() => {
        const headerInputs = document.querySelectorAll('#tabHeaderFrames textarea');
        const footerInputs = document.querySelectorAll('#tabFooterFrames textarea');
        
        if (headerInputs.length > 0) {
            currentHeaderFrame = (currentHeaderFrame + 1) % headerInputs.length;
            const headerText = headerInputs[currentHeaderFrame].value;
            document.getElementById('tabHeaderPreview').innerHTML = parseMinecraftColors(headerText);
        }
        
        if (footerInputs.length > 0) {
            currentFooterFrame = (currentFooterFrame + 1) % footerInputs.length;
            const footerText = footerInputs[currentFooterFrame].value;
            document.getElementById('tabFooterPreview').innerHTML = parseMinecraftColors(footerText);
        }
    }, 2000); // Change frame every 2 seconds
}

function parseMinecraftColors(text) {
    if (!text) return '';
    
    // Parse hex colors (&#RRGGBB) - REMOVE &m to avoid strikethrough
    text = text.replace(/&#([0-9A-Fa-f]{6})&m/g, '&#$1'); // Remove &m after hex
    text = text.replace(/&#([0-9A-Fa-f]{6})/g, (match, hex) => {
        return `<span style="color: #${hex};">`;
    });
    
    // Close color spans at the end
    const colorCount = (text.match(/&#[0-9A-Fa-f]{6}/g) || []).length;
    text += '</span>'.repeat(colorCount);
    
    // Parse & color codes
    const colorMap = {
        '0': '#000000', '1': '#0000AA', '2': '#00AA00', '3': '#00AAAA',
        '4': '#AA0000', '5': '#AA00AA', '6': '#FFAA00', '7': '#AAAAAA',
        '8': '#555555', '9': '#5555FF', 'a': '#55FF55', 'b': '#55FFFF',
        'c': '#FF5555', 'd': '#FF55FF', 'e': '#FFFF55', 'f': '#FFFFFF'
    };
    
    text = text.replace(/&([0-9a-f])/gi, (match, code) => {
        const color = colorMap[code.toLowerCase()];
        return color ? `<span style="color: ${color};">` : match;
    });
    
    // Parse MiniMessage gradients
    text = text.replace(/<gradient:(#[0-9A-Fa-f]{6}):(#[0-9A-Fa-f]{6})>(.*?)<\/gradient>/g, 
        (match, c1, c2, content) => {
            return `<span style="background: linear-gradient(90deg, ${c1}, ${c2}); -webkit-background-clip: text; -webkit-text-fill-color: transparent;">${content}</span>`;
        }
    );
    
    // Parse formatting codes (but SKIP &m to avoid strikethrough)
    text = text.replace(/&l/g, '<span style="font-weight: bold;">');
    // REMOVED: text = text.replace(/&m/g, '<span style="text-decoration: line-through;">');
    
    // Replace \n with <br>
    text = text.replace(/\\n|\n/g, '<br>');
    
    return text;
}

// Initialize when section is shown (optimized)
let tabBuilderInitialized = false;

document.addEventListener('DOMContentLoaded', () => {
    // Listen for tab section click
    const tabNavLink = document.querySelector('[data-section="tab"]');
    if (tabNavLink) {
        tabNavLink.addEventListener('click', () => {
            // Small delay to ensure DOM is ready
            setTimeout(() => {
                if (!tabBuilderInitialized && document.getElementById('tabHeaderFrames')) {
                    initTabBuilder();
                    tabBuilderInitialized = true;
                }
            }, 50); // Reduced delay
        });
    }
});