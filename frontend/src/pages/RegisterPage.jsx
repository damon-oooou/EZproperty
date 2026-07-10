import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { register } from '../api/client';
import GoogleSignInButton from '../components/GoogleSignInButton';

function RegisterPage() {
  const navigate = useNavigate();
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [agencyName, setAgencyName] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await register({ fullName, email, password, agencyName });
      navigate('/', { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  const inputCls =
    'w-full rounded-lg border border-slate-300 px-3 py-2 text-sm ' +
    'focus:outline-none focus:ring-2 focus:ring-teal-600 focus:border-teal-600';

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center px-6 py-10">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <span className="text-3xl font-semibold tracking-tight">
            <span className="text-teal-700">EZ</span>
            <span className="text-slate-900">property</span>
          </span>
          <p className="mt-2 text-sm text-slate-500">Create your account</p>
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
            <label className="block text-sm font-medium text-slate-700 mb-1">Full name</label>
            <input type="text" required autoFocus value={fullName}
                   onChange={(e) => setFullName(e.target.value)} className={inputCls} />
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">Email</label>
            <input type="email" required value={email}
                   onChange={(e) => setEmail(e.target.value)} className={inputCls} />
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">Password</label>
            <input type="password" required minLength={8} value={password}
                   onChange={(e) => setPassword(e.target.value)} className={inputCls} />
            <p className="mt-1 text-xs text-slate-400">At least 8 characters</p>
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">
              Agency name <span className="text-slate-400 font-normal">(optional)</span>
            </label>
            <input type="text" value={agencyName} placeholder="Your agency or business name"
                   onChange={(e) => setAgencyName(e.target.value)} className={inputCls} />
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="w-full py-2 rounded-lg text-sm font-medium bg-teal-700 text-white
                       hover:bg-teal-800 transition-colors disabled:opacity-60"
          >
            {submitting ? 'Creating account...' : 'Create account'}
          </button>

          <GoogleSignInButton
            onSuccess={() => navigate('/', { replace: true })}
            onError={setError}
          />
        </form>

        <p className="mt-4 text-center text-sm text-slate-500">
          Already have an account?{' '}
          <Link to="/login" className="text-teal-700 font-medium hover:underline">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
}

export default RegisterPage;
