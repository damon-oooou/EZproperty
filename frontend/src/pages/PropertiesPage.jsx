import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getProperties, createProperty, getStoredUser, logout } from '../api/client';

/** 搜索词高亮:地址中匹配的片段加粗 */
function Highlight({ text, query }) {
  const q = query.trim().toLowerCase();
  const idx = text.toLowerCase().indexOf(q);
  if (!q || idx === -1) return text;
  return (
    <>
      {text.slice(0, idx)}
      <span className="font-semibold text-slate-900">{text.slice(idx, idx + q.length)}</span>
      {text.slice(idx + q.length)}
    </>
  );
}

function PropertiesPage() {
  const navigate = useNavigate();

  const [properties, setProperties] = useState([]);
  const [query, setQuery] = useState('');
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);

  // Add Property 弹窗状态
  const [modalOpen, setModalOpen] = useState(false);
  const [address, setAddress] = useState('');
  const [type, setType] = useState('HOUSE');
  const [creating, setCreating] = useState(false);

  const searchRef = useRef(null);
  const inputRef = useRef(null);

  useEffect(() => {
    getProperties().then(setProperties);
    inputRef.current?.focus();
  }, []);

  // 点击搜索区域以外时关闭下拉
  useEffect(() => {
    function handleClickOutside(e) {
      if (searchRef.current && !searchRef.current.contains(e.target)) {
        setDropdownOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const matches = query.trim()
    ? properties.filter((p) =>
        p.address.toLowerCase().includes(query.trim().toLowerCase())
      )
    : [];

  function handleQueryChange(e) {
    setQuery(e.target.value);
    setActiveIndex(-1);
    setDropdownOpen(true);
  }

  function openCreateModal(prefillAddress = '') {
    setAddress(prefillAddress);
    setType('HOUSE');
    setDropdownOpen(false);
    setModalOpen(true);
  }

  function handleKeyDown(e) {
    if (e.key === 'Escape') {
      setDropdownOpen(false);
      return;
    }
    if (!dropdownOpen || !query.trim()) return;

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActiveIndex((i) => Math.min(i + 1, matches.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActiveIndex((i) => Math.max(i - 1, -1));
    } else if (e.key === 'Enter') {
      if (activeIndex >= 0 && matches[activeIndex]) {
        navigate(`/properties/${matches[activeIndex].id}`);
      } else if (matches.length > 0) {
        navigate(`/properties/${matches[0].id}`);
      } else {
        openCreateModal(query.trim());
      }
    }
  }

  async function handleCreate() {
    if (!address.trim() || creating) return;
    setCreating(true);
    try {
      const created = await createProperty(address.trim(), type);
      navigate(`/properties/${created.id}`);
    } finally {
      setCreating(false);
    }
  }

  const user = getStoredUser();

  function handleLogout() {
    logout();
    navigate('/login');
  }

  return (
    <div className="min-h-screen bg-white flex flex-col items-center justify-center px-6 pb-24">
      {/* v0.5:首页不走 Layout(全屏搜索设计),用户信息单独固定在右上角 */}
      {user && (
        <div className="absolute top-0 right-0 p-4 flex items-center gap-3">
          <span className="text-sm text-slate-500" title={user.email}>
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

      {/* Wordmark */}
      <h1 className="text-5xl font-semibold tracking-tight mb-2 select-none">
        <span className="text-teal-700">EZ</span>
        <span className="text-slate-900">property</span>
      </h1>
      <p className="text-sm text-slate-400 mb-10">
        Persistent photo history for every property
      </p>

      {/* 搜索 + Add Property */}
      <div className="w-full max-w-xl flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
        <div ref={searchRef} className="relative flex-1">
          <svg
            className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400 pointer-events-none"
            fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2"
          >
            <path strokeLinecap="round" strokeLinejoin="round"
              d="M21 21l-4.35-4.35M17 11a6 6 0 11-12 0 6 6 0 0112 0z" />
          </svg>
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={handleQueryChange}
            onFocus={() => setDropdownOpen(true)}
            onKeyDown={handleKeyDown}
            placeholder="Search properties by address..."
            className="w-full h-12 pl-12 pr-4 rounded-full border border-slate-300 shadow-sm
                       text-slate-900 placeholder:text-slate-400
                       focus:outline-none focus:border-teal-600 focus:ring-2 focus:ring-teal-600/20"
          />

          {/* 自动补全下拉 */}
          {dropdownOpen && query.trim() && (
            <div className="absolute top-full left-0 right-0 mt-2 bg-white border border-slate-200
                            rounded-2xl shadow-lg overflow-hidden z-10">
              {matches.length > 0 ? (
                <ul>
                  {matches.map((p, i) => (
                    <li key={p.id}>
                      <button
                        onClick={() => navigate(`/properties/${p.id}`)}
                        onMouseEnter={() => setActiveIndex(i)}
                        className={`w-full text-left px-4 py-3 flex items-center justify-between gap-3
                                    ${i === activeIndex ? 'bg-slate-50' : ''}`}
                      >
                        <span className="text-slate-600 truncate">
                          <Highlight text={p.address} query={query} />
                        </span>
                        <span className="shrink-0 text-xs uppercase tracking-wide text-slate-400">
                          {p.type}
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              ) : (
                <button
                  onClick={() => openCreateModal(query.trim())}
                  className="w-full text-left px-4 py-3 text-slate-500 hover:bg-slate-50"
                >
                  No properties found —{' '}
                  <span className="text-teal-700 font-medium">
                    create "{query.trim()}"
                  </span>
                </button>
              )}
            </div>
          )}
        </div>

        <button
          onClick={() => openCreateModal()}
          className="h-12 px-5 rounded-full bg-teal-700 text-white font-medium shrink-0
                     hover:bg-teal-800 focus:outline-none focus:ring-2 focus:ring-teal-600/40"
        >
          + Add Property
        </button>
      </div>

      {/* Add Property 弹窗 */}
      {modalOpen && (
        <div
          className="fixed inset-0 bg-slate-900/40 flex items-center justify-center px-6 z-20"
          onMouseDown={(e) => {
            if (e.target === e.currentTarget) setModalOpen(false);
          }}
        >
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-md p-6">
            <h2 className="text-lg font-semibold text-slate-900 mb-4">New property</h2>

            <label className="block text-sm font-medium text-slate-600 mb-1">Address</label>
            <input
              type="text"
              value={address}
              onChange={(e) => setAddress(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleCreate()}
              placeholder="e.g. 12 Smith St, Richmond VIC"
              autoFocus
              className="w-full h-10 px-3 mb-4 rounded-lg border border-slate-300
                         focus:outline-none focus:border-teal-600 focus:ring-2 focus:ring-teal-600/20"
            />

            <label className="block text-sm font-medium text-slate-600 mb-1">Type</label>
            <select
              value={type}
              onChange={(e) => setType(e.target.value)}
              className="w-full h-10 px-2 mb-6 rounded-lg border border-slate-300 bg-white
                         focus:outline-none focus:border-teal-600 focus:ring-2 focus:ring-teal-600/20"
            >
              <option value="HOUSE">House</option>
              <option value="APARTMENT">Apartment</option>
            </select>

            <div className="flex justify-end gap-3">
              <button
                onClick={() => setModalOpen(false)}
                className="h-10 px-4 rounded-lg text-slate-600 hover:bg-slate-100"
              >
                Cancel
              </button>
              <button
                onClick={handleCreate}
                disabled={!address.trim() || creating}
                className="h-10 px-4 rounded-lg bg-teal-700 text-white font-medium
                           hover:bg-teal-800 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {creating ? 'Creating...' : 'Create property'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default PropertiesPage;