import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { ApiError } from '@/lib/api';

export const Login: React.FC = () => {
  const navigate = useNavigate();
  const { login } = useAuth();

  const [username, setUsername] = useState<string>('');
  const [password, setPassword] = useState<string>('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState<boolean>(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await login(username.trim(), password);
      navigate('/');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Sign in failed. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#F0EDE8] flex flex-col items-center justify-center p-6 select-none">
      {/* Brand Header */}
      <div className="mb-8 text-center flex items-center justify-center gap-3.5">
        <div className="w-12 h-12 rounded-[10px] bg-[#1A1A1A] flex items-center justify-center shadow-card shrink-0">
          <svg width="28" height="28" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
            <defs>
              <mask id="loginAntEye">
                <rect width="48" height="48" fill="#FFFFFF" />
                <circle cx="34" cy="17" r="1.4" fill="#000000" />
              </mask>
            </defs>
            <g mask="url(#loginAntEye)">
              {/* Antennae */}
              <path d="M 31.5 12 C 33.5 6.5 38 5 41.5 6.5" stroke="#FFFFFF" strokeWidth="2.2" strokeLinecap="round" />
              <path d="M 28.5 12.5 C 29.5 8 33 6 36.5 7" stroke="#FFFFFF" strokeWidth="2" strokeLinecap="round" />

              {/* 3 Standing Legs */}
              <path d="M 25 28 L 29.5 35 L 34.5 41" stroke="#FFFFFF" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
              <path d="M 22.5 29 L 23 36 L 24 41" stroke="#FFFFFF" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
              <path d="M 20 29 L 16 35 L 13.5 41" stroke="#FFFFFF" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />

              {/* Body Segments */}
              <ellipse cx="11.5" cy="27" rx="6.8" ry="8.5" fill="#FFFFFF" transform="rotate(-30 11.5 27)" />
              <ellipse cx="17.5" cy="26" rx="2" ry="2.5" fill="#FFFFFF" />
              <ellipse cx="23" cy="24.5" rx="4.8" ry="5.8" fill="#FFFFFF" transform="rotate(-15 23 24.5)" />
              <ellipse cx="27" cy="20.5" rx="2.5" ry="3" fill="#FFFFFF" />
              <ellipse cx="32" cy="18" rx="6" ry="6.2" fill="#FFFFFF" />
            </g>
          </svg>
        </div>
        <h1 className="text-[32px] tracking-tight font-sans leading-none">
          <span className="font-bold text-[#E8622A]">Alloca</span>
          <span className="font-light text-[#1A1A1A]">Space</span>
        </h1>
      </div>

      {/* Login Card (Squarer rounded-[8px]) */}
      <div className="w-full max-w-md bg-[#FAFAF8] rounded-[8px] shadow-card p-8 space-y-6 border-0">
        <div>
          <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">
            Authentication
          </span>
          <h2 className="text-xl font-light text-[#1A1A1A] font-editorial mt-0.5">
            Sign in to workspace
          </h2>
          <p className="text-xs text-[#8A8070] mt-1">
            Access spatial scheduling, room allocations, and resource directory
          </p>
        </div>

        {/* Credentials Form */}
        <form onSubmit={handleSubmit} className="space-y-4">
          {error && (
            <div role="alert" className="p-3 rounded-[6px] bg-[#FFEBEE] text-[#C62828] text-xs font-semibold">
              {error}
            </div>
          )}

          <div>
            <label className="block text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] mb-1">
              Username
            </label>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="w-full h-11 px-3.5 rounded-[6px] bg-[#F0EDE8]/60 text-xs font-semibold text-[#1A1A1A] border-0 focus:ring-1 focus:ring-[#8A8070] focus:outline-none"
              autoComplete="username"
              required
            />
          </div>

          <div>
            <label className="block text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] mb-1">
              Password
            </label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full h-11 px-3.5 rounded-[6px] bg-[#F0EDE8]/60 text-xs font-semibold text-[#1A1A1A] border-0 focus:ring-1 focus:ring-[#8A8070] focus:outline-none"
              autoComplete="current-password"
              required
            />
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="w-full py-2.5 rounded-[6px] bg-[#E8622A] text-white text-xs font-semibold hover:opacity-95 shadow-card hover:shadow-card-hover transition-all cursor-pointer mt-4 disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {submitting ? 'Signing in…' : 'Access Spatial Dashboard'}
          </button>
        </form>
      </div>
    </div>
  );
};
