import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { login } from '../api/client';
import GoogleSignInButton from '../components/GoogleSignInButton';

function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      // 登录前想访问的页面(被 RequireAuth 拦下时记录),登录后跳回去
      navigate(location.state?.from?.pathname || '/', { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center px-6">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <span className="text-3xl font-semibold tracking-tight">
            <span className="text-teal-700">EZ</span>
            <span className="text-slate-900">property</span>
          </span>
          <p className="mt-2 text-sm text-slate-500">Sign in to your account</p>
        </div>

        <form
          onSubmit={handleSubmit}
          className="rounded-xl bg-white border border-slate-200 p-6 space-y-4"
        >
          {error && (
            <div className="rounded-lg bg-red-50 border border-red-200 px-3 py-2 text-sm text-red-700">
              {error}
            </div>
          )}

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">Email</label>
            <input
              type="email"
              required
              autoFocus
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm
                         focus:outline-none focus:ring-2 focus:ring-teal-600 focus:border-teal-600"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">Password</label>
            <input
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm
                         focus:outline-none focus:ring-2 focus:ring-teal-600 focus:border-teal-600"
            />
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="w-full py-2 rounded-lg text-sm font-medium bg-teal-700 text-white
                       hover:bg-teal-800 transition-colors disabled:opacity-60"
          >
            {submitting ? 'Signing in...' : 'Sign in'}
          </button>

          <GoogleSignInButton
            onSuccess={() => navigate(location.state?.from?.pathname || '/', { replace: true })}
            onError={setError}
          />
        </form>

        <p className="mt-4 text-center text-sm text-slate-500">
          Don&apos;t have an account?{' '}
          <Link to="/register" className="text-teal-700 font-medium hover:underline">
            Create one
          </Link>
        </p>
      </div>
    </div>
  );
}

export default LoginPage;
