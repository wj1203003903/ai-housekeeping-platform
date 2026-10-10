function appendMessage(message, type) {
    const chatMessages = document.getElementById('chatbox-messages');
    const messageElement = document.createElement('div');
    messageElement.classList.add('chat-bubble', type);

    // Model output is untrusted. Render it as text instead of executable HTML/Markdown.
    messageElement.textContent = String(message);

    chatMessages.appendChild(messageElement);

    // Scroll to the bottom of the chatbox to show the latest message
    chatMessages.scrollTop = chatMessages.scrollHeight;
    return messageElement;
}

function getConversationId() {
    let conversationId = localStorage.getItem('housekeepingConversationId');
    if (!conversationId) {
        conversationId = (crypto.randomUUID ? crypto.randomUUID() : Date.now().toString(36));
        localStorage.setItem('housekeepingConversationId', conversationId);
    }
    return conversationId;
}

function authHeaders() {
    const token = HousekeepingAuth.getAccessToken();
    return token ? { 'Authorization': `Bearer ${token}` } : {};
}

function appendConfirmation(messageElement, pendingAction) {
    if (!pendingAction?.token) return;
    const button = document.createElement('button');
    button.className = 'ai-confirm-action';
    button.textContent = '确认执行';
    button.onclick = async () => {
        button.disabled = true;
        button.textContent = '正在执行…';
        try {
            const response = await HousekeepingAuth.fetch(`/api/genai/actions/${encodeURIComponent(pendingAction.token)}/confirm`, {
                method: 'POST', headers: authHeaders()
            });
            if (!response.ok) throw new Error(await response.text());
            const result = await response.json();
            button.textContent = '已执行';
            appendMessage(`操作成功：${result.bookingNumber || result.status || '已完成'}`, 'bot');
        } catch (error) {
            button.disabled = false;
            button.textContent = '重试确认';
            appendMessage(`操作未执行：${error.message}`, 'bot');
        }
    };
    messageElement.appendChild(button);
}

function toggleChatbox() {
    const chatbox = document.getElementById('chatbox');
    const chatboxContent = document.getElementById('chatbox-content');

    if (chatbox.classList.contains('minimized')) {
        chatbox.classList.remove('minimized');
        chatboxContent.style.height = '400px'; // Set to initial height when expanded
    } else {
        chatbox.classList.add('minimized');
        chatboxContent.style.height = '40px'; // Set to minimized height
    }
}

function sendMessage() {
    const query = document.getElementById('chatbox-input').value;

    // Only send if there's a message
    if (!query.trim()) return;

    // Clear the input field after sending the message
    document.getElementById('chatbox-input').value = '';

    // Display user message in the chatbox
    appendMessage(query, 'user');

    // Send the message to the backend
    HousekeepingAuth.fetch('/api/genai/chatclient', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            ...authHeaders(),
        },
        body: JSON.stringify({ message: query, conversationId: getConversationId() }),
    })
        .then(async response => {
            if (!response.ok) throw new Error((await response.json().catch(() => null))?.detail || `HTTP ${response.status}`);
            return response.json();
        })
        .then(response => {
            localStorage.setItem('housekeepingConversationId', response.conversationId);
            const element = appendMessage(response.message, 'bot');
            (response.pendingActions || []).forEach(action => appendConfirmation(element, action));
        })
        .catch(error => {
            console.error('Error:', error);
            // Display the fallback message in the chatbox
            appendMessage(`AI 服务暂时不可用：${error.message}`, 'bot');
        });
}

function handleKeyPress(event) {
    if (event.key === "Enter") {
        event.preventDefault(); // Prevents adding a newline
        sendMessage(); // Send the message when Enter is pressed
    }
}

// Save chat messages to localStorage
function saveChatMessages() {
    // Never persist executable DOM fragments in browser storage.
    localStorage.removeItem('chatMessages');
}

// Load chat messages from localStorage
function loadChatMessages() {
    localStorage.removeItem('chatMessages');
}

document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('toggleChatbox').addEventListener('click', toggleChatbox);
    document.getElementById('sendChatMessage').addEventListener('click', sendMessage);
    document.getElementById('chatbox-input').addEventListener('keydown', handleKeyPress);
});
