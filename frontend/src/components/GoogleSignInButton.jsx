import { useEffect, useRef, useState } from 'react';
import { googleLogin } from '../api/client';

const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID;

/** 动态加载 Google Identity Services 脚本(只加载一次,后续调用直接复用)。 */
let gisPromise = null;
function loadGis() {
  if (window.google?.accounts?.id) return Promise.resolve();
  if (gisPromise) return gisPromise;
  gisPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = 'https://accounts.google.com/gsi/client';
    script.async = true;
    script.onload = resolve;
    script.onerror = () => {
      gisPromise = null; // 失败后允许下次重试
      reject(new Error('Failed to load Google sign-in'));
    };
    document.head.appendChild(script);
  });
  return gisPromise;
}

/**
 * Google 官方登录按钮。未配置 VITE_GOOGLE_CLIENT_ID 时不渲染,
 * 密码登录不受影响。onSuccess 在换取本站 JWT 成功后触发。
 */
function GoogleSignInButton({ onSuccess, onError }) {
  const containerRef = useRef(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!CLIENT_ID) return;
    let cancelled = false;

    loadGis()
      .then(() => {
        if (cancelled || !containerRef.current) return;
        window.google.accounts.id.initialize({
          client_id: CLIENT_ID,
          callback: async (response) => {
            try {
              await googleLogin(response.credential);
              onSuccess();
            } catch (err) {
              onError?.(err.message);
            }
          },
        });
        window.google.accounts.id.renderButton(containerRef.current, {
          theme: 'outline',
          size: 'large',
          width: 300,
          text: 'continue_with',
        });
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      });

    return () => { cancelled = true; };
    // onSuccess/onError 引用变化不需要重新初始化按钮
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!CLIENT_ID || failed) return null;

  return (
    <>
      <div className="flex items-center gap-3 my-1">
        <div className="flex-1 h-px bg-slate-200" />
        <span className="text-xs text-slate-400">or</span>
        <div className="flex-1 h-px bg-slate-200" />
      </div>
      <div ref={containerRef} className="flex justify-center" />
    </>
  );
}

export default GoogleSignInButton;
