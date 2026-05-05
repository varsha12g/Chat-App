import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { DoorOpen, Hash, LogOut, Send, Users } from 'lucide-react';
import { backendUrl, chatApi } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';

export default function Chat() {
  const { token, user, logout } = useAuth();
  const [rooms, setRooms] = useState([]);
  const [activeRoomId, setActiveRoomId] = useState('');
  const [roomIdInput, setRoomIdInput] = useState('');
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState('');
  const [error, setError] = useState('');
  const [connected, setConnected] = useState(false);
  const clientRef = useRef(null);
  const messagesEndRef = useRef(null);

  const activeRoom = useMemo(
    () => rooms.find((room) => room.id === activeRoomId),
    [rooms, activeRoomId],
  );

  useEffect(() => {
    loadRooms();
  }, []);

  useEffect(() => {
    if (!activeRoomId) {
      return undefined;
    }
    const interval = window.setInterval(() => {
      loadRooms({ keepActiveRoom: true });
    }, 5000);
    return () => window.clearInterval(interval);
  }, [activeRoomId]);

  const loadMessages = useCallback(async (roomId) => {
    setError('');
    try {
      setMessages(await chatApi.messages(roomId));
    } catch (err) {
      setError(err.message);
    }
  }, []);

  const connectToRoom = useCallback((roomId) => {
    clientRef.current?.deactivate();
    const client = new Client({
      webSocketFactory: () => new SockJS(backendUrl('/chat')),
      reconnectDelay: 4000,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/topic/${roomId}`, (frame) => {
          const nextMessage = JSON.parse(frame.body);
          setMessages((current) => {
            if (current.some((message) => message.id === nextMessage.id)) {
              return current;
            }
            return [...current, nextMessage];
          });
        });
      },
      onDisconnect: () => setConnected(false),
      onStompError: (frame) => setError(frame.headers.message || 'WebSocket error'),
      onWebSocketError: () => setError('Could not connect to realtime chat'),
    });
    client.activate();
    clientRef.current = client;
  }, [token]);

  useEffect(() => {
    if (!activeRoomId) {
      setMessages([]);
      return undefined;
    }
    loadMessages(activeRoomId);
    connectToRoom(activeRoomId);
    return () => {
      clientRef.current?.deactivate();
      clientRef.current = null;
      setConnected(false);
    };
  }, [activeRoomId, connectToRoom, loadMessages]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  async function loadRooms(options = {}) {
    setError('');
    try {
      const roomList = await chatApi.rooms();
      setRooms(roomList);
      if (!options.keepActiveRoom && roomList.length > 0) {
        setActiveRoomId(roomList[0].id);
      }
    } catch (err) {
      setError(err.message);
    }
  }

  async function joinRoom(event) {
    event.preventDefault();
    const roomId = roomIdInput.trim();
    if (!roomId) {
      return;
    }

    setError('');
    try {
      const room = await chatApi.joinRoom(roomId);
      setRooms((current) => {
        const withoutDuplicate = current.filter((item) => item.id !== room.id);
        return [room, ...withoutDuplicate];
      });
      setActiveRoomId(room.id);
      setRoomIdInput('');
    } catch (err) {
      setError(err.message);
    }
  }

  function sendMessage(event) {
    event.preventDefault();
    const content = draft.trim();
    if (!content || !clientRef.current?.connected || !activeRoomId) {
      return;
    }
    clientRef.current.publish({
      destination: `/app/sendMessage/${activeRoomId}`,
      body: JSON.stringify({ content }),
    });
    setDraft('');
  }

  return (
    <main className="chat-shell">
      <aside className="sidebar">
        <div className="workspace-header">
          <div>
            <span className="eyebrow">Relay</span>
            <h1>Chat</h1>
          </div>
        </div>

        <div className="profile-strip">
          <span className="avatar">{initials(user?.username)}</span>
          <div>
            <strong>{user?.username}</strong>
            <p>{connected ? 'Realtime connected' : 'Logged in'}</p>
          </div>
          <button className="icon-button ghost" onClick={logout} title="Log out">
            <LogOut size={18} />
          </button>
        </div>

        <form className="join-card" onSubmit={joinRoom}>
          <label>
            Enter room id
            <div className="join-input">
              <Hash size={17} />
              <input
                value={roomIdInput}
                onChange={(event) => setRoomIdInput(event.target.value)}
                placeholder="valorant"
                minLength={3}
                maxLength={40}
                required
              />
            </div>
          </label>
          <button className="primary-button">
            <DoorOpen size={18} />
            Join room
          </button>
        </form>

        <div className="connected-card">
          <span className="eyebrow">Connected</span>
          {activeRoom ? (
            <>
              <strong>#{activeRoom.id}</strong>
              <p>
                <Users size={16} />
                {activeRoom.memberIds.length} user{activeRoom.memberIds.length === 1 ? '' : 's'} joined
              </p>
            </>
          ) : (
            <p>No room joined</p>
          )}
        </div>
      </aside>

      <section className="conversation">
        <header className="conversation-header">
          <div>
            <span className="eyebrow">{activeRoom ? `${activeRoom.memberIds.length} joined` : 'No room joined'}</span>
            <h2>{activeRoom ? `#${activeRoom.id}` : 'Join a room'}</h2>
          </div>
          <span className={`status-pill ${connected ? 'online' : ''}`}>
            {connected ? 'Live' : 'Idle'}
          </span>
        </header>

        <div className="message-stream">
          {error && <p className="app-error">{error}</p>}
          {!activeRoom && (
            <div className="empty-state">
              <Hash size={30} />
              <p>Type a room ID on the left. Same room ID means same chat.</p>
            </div>
          )}
          {messages.map((message) => {
            const own = message.senderId === user?.id;
            return (
              <article key={message.id} className={`message ${own ? 'own' : ''}`}>
                <div className="message-meta">
                  <strong>{own ? 'You' : message.senderUsername}</strong>
                  <time>{formatTime(message.createdAt)}</time>
                </div>
                <p>{message.content}</p>
              </article>
            );
          })}
          <div ref={messagesEndRef} />
        </div>

        <form className="message-composer" onSubmit={sendMessage}>
          <input
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            placeholder={activeRoom ? 'Type a message' : 'Join a room first'}
            disabled={!activeRoom}
          />
          <button className="send-button" disabled={!draft.trim() || !activeRoom || !connected} title="Send">
            <Send size={19} />
          </button>
        </form>
      </section>
    </main>
  );
}

function initials(name = '') {
  return name.slice(0, 2).toUpperCase() || 'R';
}

function formatTime(value) {
  if (!value) {
    return '';
  }
  return new Intl.DateTimeFormat(undefined, {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}
