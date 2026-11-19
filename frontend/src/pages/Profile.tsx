import React, { useState } from 'react';
import { ApiError } from '@/lib/api';
import { useAuth } from '@/context/AuthContext';
import type { User } from '@/types/api';

const inputCls =
  'w-full h-10 px-3 rounded-[6px] bg-[#F0EDE8]/60 text-sm text-[#242424] border border-transparent focus:outline-none focus:border-[#D97757]';
const labelCls = 'text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block mb-1.5';
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const formatError = (err: unknown, fallback: string) => {
  if (err instanceof ApiError && err.fieldErrors.length > 0) {
    return err.fieldErrors[0].message;
  }
  return err instanceof Error ? err.message : fallback;
};

const Msg = ({ m }: { m: { ok: boolean; text: string } | null }) =>
  m ? (
    <p className={`text-xs font-medium ${m.ok ? 'text-[#2E7D32]' : 'text-[#C62828]'}`} role="status">
      {m.text}
    </p>
  ) : null;

export const Profile: React.FC = () => {
  const { user, updateProfile, changePassword } = useAuth();
  if (!user) return null;
  return <ProfileContent key={user.id} user={user} updateProfile={updateProfile} submitPassword={changePassword} />;
};

const ProfileContent: React.FC<{
  user: User;
  updateProfile: (changes: Pick<User, 'fullName' | 'email'>) => Promise<void>;
  submitPassword: (currentPassword: string, newPassword: string) => Promise<void>;
}> = ({ user, updateProfile, submitPassword }) => {
  const [fullName, setFullName] = useState(user.fullName);
  const [email, setEmail] = useState(user.email);
  const [profileMsg, setProfileMsg] = useState<{ ok: boolean; text: string } | null>(null);

  const [currentPw, setCurrentPw] = useState('');
  const [newPw, setNewPw] = useState('');
  const [confirmPw, setConfirmPw] = useState('');
  const [pwMsg, setPwMsg] = useState<{ ok: boolean; text: string } | null>(null);

  const dirty = fullName.trim() !== user.fullName || email.trim() !== user.email;

  const saveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!fullName.trim()) return setProfileMsg({ ok: false, text: 'Full name is required.' });
    if (!EMAIL_RE.test(email.trim())) return setProfileMsg({ ok: false, text: 'Enter a valid email address.' });
    try {
      await updateProfile({ fullName: fullName.trim(), email: email.trim() });
      setProfileMsg({ ok: true, text: 'Profile updated.' });
    } catch (err) {
      setProfileMsg({ ok: false, text: formatError(err, 'Update failed.') });
    }
  };

  const changePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentPw) return setPwMsg({ ok: false, text: 'Enter your current password.' });
    if (newPw.length < 8) return setPwMsg({ ok: false, text: 'New password must be at least 8 characters.' });
    if (newPw === currentPw) return setPwMsg({ ok: false, text: 'New password must differ from the current one.' });
    if (newPw !== confirmPw) return setPwMsg({ ok: false, text: 'Passwords do not match.' });
    try {
      await submitPassword(currentPw, newPw);
      setCurrentPw('');
      setNewPw('');
      setConfirmPw('');
      setPwMsg({ ok: true, text: 'Password changed.' });
    } catch (err) {
      setPwMsg({ ok: false, text: formatError(err, 'Password change failed.') });
    }
  };

  return (
    <div className="flex flex-col w-full p-6 lg:p-8 space-y-6 bg-[#F0EDE8] min-h-screen max-w-3xl">
      <div>
        <span className="text-[10px] uppercase tracking-widest font-semibold text-[#8A8070] block">Account</span>
        <h1 className="text-2xl font-light text-[#242424] font-editorial">My Profile</h1>
      </div>

      <form onSubmit={saveProfile} className="bg-[#FAFAF8] p-6 rounded-[8px] shadow-card space-y-4">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className={labelCls} htmlFor="fullName">Full name</label>
            <input id="fullName" className={inputCls} value={fullName} onChange={(e) => { setFullName(e.target.value); setProfileMsg(null); }} />
          </div>
          <div>
            <label className={labelCls} htmlFor="email">Email</label>
            <input id="email" type="email" className={inputCls} value={email} onChange={(e) => { setEmail(e.target.value); setProfileMsg(null); }} />
          </div>
          <div>
            <label className={labelCls}>Username</label>
            <input className={`${inputCls} opacity-60`} value={user.username} disabled />
          </div>
          <div>
            <label className={labelCls}>Role</label>
            <input className={`${inputCls} opacity-60`} value={user.role} disabled />
          </div>
          <div>
            <label className={labelCls}>Department</label>
            <input className={`${inputCls} opacity-60`} value={user.department?.name ?? '—'} disabled />
          </div>
          <div>
            <label className={labelCls}>Status</label>
            <input className={`${inputCls} opacity-60`} value={user.isActive ? 'Active' : 'Inactive'} disabled />
          </div>
        </div>
        <p className="text-[11px] text-[#8A8070]">Role, department and status are managed by an administrator.</p>
        <div className="flex items-center gap-4">
          <button
            type="submit"
            disabled={!dirty}
            className="px-5 py-2 rounded-[20px] bg-[#D97757] text-white text-xs font-medium hover:bg-[#C56546] disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
          >
            Save changes
          </button>
          <Msg m={profileMsg} />
        </div>
      </form>

      <form onSubmit={changePassword} className="bg-[#FAFAF8] p-6 rounded-[8px] shadow-card space-y-4">
        <h2 className="text-base font-bold text-[#242424] font-editorial">Change password</h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div>
            <label className={labelCls} htmlFor="currentPw">Current password</label>
            <input id="currentPw" type="password" autoComplete="current-password" className={inputCls} value={currentPw} onChange={(e) => { setCurrentPw(e.target.value); setPwMsg(null); }} />
          </div>
          <div>
            <label className={labelCls} htmlFor="newPw">New password</label>
            <input id="newPw" type="password" autoComplete="new-password" className={inputCls} value={newPw} onChange={(e) => { setNewPw(e.target.value); setPwMsg(null); }} />
          </div>
          <div>
            <label className={labelCls} htmlFor="confirmPw">Confirm new password</label>
            <input id="confirmPw" type="password" autoComplete="new-password" className={inputCls} value={confirmPw} onChange={(e) => { setConfirmPw(e.target.value); setPwMsg(null); }} />
          </div>
        </div>
        <div className="flex items-center gap-4">
          <button type="submit" className="px-5 py-2 rounded-[20px] bg-[#242424] text-white text-xs font-medium hover:bg-black cursor-pointer">
            Update password
          </button>
          <Msg m={pwMsg} />
        </div>
      </form>
    </div>
  );
};
