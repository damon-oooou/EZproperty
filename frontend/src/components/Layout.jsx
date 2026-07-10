import { Link, useNavigate } from 'react-router-dom';
import { getStoredUser, logout } from '../api/client';

function Layout({ breadcrumbs = [], children }) {
  const navigate = useNavigate();
  const user = getStoredUser();

  function handleLogout() {
    logout();
    navigate('/login');
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="bg-white border-b border-slate-200">
        <div className="max-w-5xl mx-auto px-6 h-14 flex items-center gap-6">
          <Link to="/" className="text-lg font-semibold tracking-tight shrink-0">
            <span className="text-teal-700">EZ</span>
            <span className="text-slate-900">property</span>
          </Link>

          {breadcrumbs.length > 0 && (
            <nav className="flex items-center gap-2 text-sm text-slate-500 min-w-0">
              {breadcrumbs.map((crumb, i) => {
                const isLast = i === breadcrumbs.length - 1;
                return (
                  <span key={i} className="flex items-center gap-2 min-w-0">
                    {i > 0 && <span className="text-slate-300">/</span>}
                    {isLast || !crumb.to ? (
                      <span className="text-slate-900 font-medium truncate">{crumb.label}</span>
                    ) : (
                      <Link to={crumb.to} className="hover:text-teal-700 truncate">
                        {crumb.label}
                      </Link>
                    )}
                  </span>
                );
              })}
            </nav>
          )}

          {/* v0.5:当前用户 + 登出 */}
          {user && (
            <div className="ml-auto flex items-center gap-3 shrink-0">
              <span className="text-sm text-slate-500 hidden sm:inline" title={user.email}>
                {user.fullName}
              </span>
              <button
                onClick={handleLogout}
                className="text-sm text-slate-500 hover:text-teal-700 transition-colors"
              >
                Log out
              </button>
            </div>
          )}
        </div>
      </header>

      <main className="max-w-5xl mx-auto px-6 py-8">{children}</main>
    </div>
  );
}

export default Layout;
