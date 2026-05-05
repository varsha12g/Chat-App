import { MessageCircle } from 'lucide-react';

export default function AuthShell({ title, subtitle, children }) {
  return (
    <main className="auth-screen">
      <section className="auth-panel">
        <div className="brand-mark">
          <MessageCircle size={24} />
          <span>Relay</span>
        </div>
        <div className="auth-copy">
          <h1>{title}</h1>
          <p>{subtitle}</p>
        </div>
        {children}
      </section>
    </main>
  );
}
