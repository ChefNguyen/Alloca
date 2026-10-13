import React, { useState } from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { useSidebar } from '@/context/SidebarContext';
import { mockNotifications } from '@/mock/data';
import { Role } from '@/types/api';

export const Sidebar: React.FC = () => {
  const { user, role, switchRole, logout, unreadCount, setUnreadCount } = useAuth();
  const { isExpanded, toggleCollapse } = useSidebar();

  const [showNotifications, setShowNotifications] = useState<boolean>(false);
  const [showProfileMenu, setShowProfileMenu] = useState<boolean>(false);
  const [notifications, setNotifications] = useState(mockNotifications);

  const isManagerOrAdmin = role === 'MANAGER' || role === 'ADMIN';
  const isAdmin = role === 'ADMIN';

  const markAllAsRead = () => {
    setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
    setUnreadCount(0);
  };

  // Nav item style: Active item: background #D97757, white text, rounded-lg
  // Inactive item: #6B6560 text, no background
  const navItemClass = (isActive: boolean) =>
    `flex items-center gap-3 px-3.5 py-2.5 text-xs transition-all rounded-lg cursor-pointer select-none group relative ${
      isActive
        ? 'bg-[#D97757] text-white font-medium shadow-xs'
        : 'text-[#6B6560] bg-transparent hover:bg-[#EAE8E4] hover:text-[#242424]'
    }`;

  const navItemCollapsedClass = (isActive: boolean) =>
    `w-10 h-10 mx-auto flex items-center justify-center rounded-lg transition-all cursor-pointer relative group ${
      isActive
        ? 'bg-[#D97757] text-white shadow-xs'
        : 'text-[#6B6560] bg-transparent hover:bg-[#EAE8E4] hover:text-[#242424]'
    }`;

  return (
    <aside
      className={`fixed left-0 top-0 h-full bg-[#F3F2EF] z-50 flex flex-col justify-between py-3 transition-all duration-200 select-none shadow-md ${
        isExpanded ? 'w-[240px]' : 'w-[56px]'
      }`}
    >
      {/* ========================================================= */}
      {/* TOP: Standing Ant (Facing Right) + AllocaSpace Wordmark   */}
      {/* ========================================================= */}
      <div className="flex flex-col">
        <div
          className={`flex items-center justify-between px-3 py-2 mb-2 ${
            !isExpanded ? 'justify-center px-0' : ''
          }`}
        >
          <NavLink
            to="/"
            title="AllocaSpace"
            className="flex items-center gap-2.5 group overflow-hidden"
          >
            {/* Minimalist Ant Logo Icon (Facing Right, Terracotta #D97757, No background box) */}
            <div className="w-8 h-8 flex items-center justify-center shrink-0 group-hover:scale-105 transition-transform">
              <svg width="32" height="32" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
                <defs>
                  <mask id="sidebarAntEyeLight">
                    <rect width="48" height="48" fill="#FFFFFF" />
                    <circle cx="34" cy="17" r="1.4" fill="#000000" />
                  </mask>
                </defs>
                <g mask="url(#sidebarAntEyeLight)">
                  {/* Antennae */}
                  <path d="M 31.5 12 C 33.5 6.5 38 5 41.5 6.5" stroke="#D97757" strokeWidth="2.2" strokeLinecap="round" />
                  <path d="M 28.5 12.5 C 29.5 8 33 6 36.5 7" stroke="#D97757" strokeWidth="2" strokeLinecap="round" />

                  {/* 3 Standing Legs */}
                  <path d="M 25 28 L 29.5 35 L 34.5 41" stroke="#D97757" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
                  <path d="M 22.5 29 L 23 36 L 24 41" stroke="#D97757" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
                  <path d="M 20 29 L 16 35 L 13.5 41" stroke="#D97757" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />

                  {/* Body Segments */}
                  <ellipse cx="11.5" cy="27" rx="6.8" ry="8.5" fill="#D97757" transform="rotate(-30 11.5 27)" />
                  <ellipse cx="17.5" cy="26" rx="2" ry="2.5" fill="#D97757" />
                  <ellipse cx="23" cy="24.5" rx="4.8" ry="5.8" fill="#D97757" transform="rotate(-15 23 24.5)" />
                  <ellipse cx="27" cy="20.5" rx="2.5" ry="3" fill="#D97757" />
                  <ellipse cx="32" cy="18" rx="6" ry="6.2" fill="#D97757" />
                </g>
              </svg>
            </div>

            {/* Typography: AllocaSpace written together */}
            {isExpanded && (
              <span className="text-[19px] tracking-tight font-sans leading-none whitespace-nowrap">
                <span className="font-bold text-[#D97757]">Alloca</span>
                <span className="font-light text-[#242424]">Space</span>
              </span>
            )}
          </NavLink>

          {/* Top Subtle Collapse Toggle Button */}
          {isExpanded && (
            <button
              onClick={toggleCollapse}
              title="Collapse sidebar"
              className="w-7 h-7 rounded-[4px] flex items-center justify-center text-[#6B6560] hover:text-[#242424] hover:bg-[#EAE8E4] transition-colors cursor-pointer ml-auto"
            >
              <span className="material-symbols-outlined text-[18px]">
                keyboard_double_arrow_left
              </span>
            </button>
          )}
        </div>

        {/* ========================================================= */}
        {/* MIDDLE: Nav Items (No section categories or dividers)     */}
        {/* ========================================================= */}
        <nav className="flex flex-col px-2 space-y-1 mt-2">
          {/* Dashboard */}
          <NavLink
            to="/"
            end
            className={({ isActive }) =>
              isExpanded ? navItemClass(isActive) : navItemCollapsedClass(isActive)
            }
          >
            <span className="material-symbols-outlined text-[20px] shrink-0 transition-colors">
              grid_view
            </span>
            {isExpanded ? (
              <span className="truncate">Dashboard</span>
            ) : (
              <div className="absolute left-full ml-3 px-2.5 py-1 bg-[#FAFAF8] border border-[#E5E0D8] text-[#242424] text-[11px] font-semibold rounded-[4px] shadow-lg whitespace-nowrap opacity-0 pointer-events-none group-hover:opacity-100 transition-opacity z-50 flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-[#D97757]" />
                <span>Dashboard</span>
              </div>
            )}
          </NavLink>

          {/* Floor Map */}
          <NavLink
            to="/find-resource"
            className={({ isActive }) =>
              isExpanded ? navItemClass(isActive) : navItemCollapsedClass(isActive)
            }
          >
            <span className="material-symbols-outlined text-[20px] shrink-0 transition-colors">
              map
            </span>
            {isExpanded ? (
              <span className="truncate">Floor Map</span>
            ) : (
              <div className="absolute left-full ml-3 px-2.5 py-1 bg-[#FAFAF8] border border-[#E5E0D8] text-[#242424] text-[11px] font-semibold rounded-[4px] shadow-lg whitespace-nowrap opacity-0 pointer-events-none group-hover:opacity-100 transition-opacity z-50 flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-[#D97757]" />
                <span>Floor Map</span>
              </div>
            )}
          </NavLink>

          {/* Bookings */}
          <NavLink
            to="/my-bookings"
            className={({ isActive }) =>
              isExpanded ? navItemClass(isActive) : navItemCollapsedClass(isActive)
            }
          >
            <span className="material-symbols-outlined text-[20px] shrink-0 transition-colors">
              calendar_today
            </span>
            {isExpanded ? (
              <span className="truncate">Bookings</span>
            ) : (
              <div className="absolute left-full ml-3 px-2.5 py-1 bg-[#FAFAF8] border border-[#E5E0D8] text-[#242424] text-[11px] font-semibold rounded-[4px] shadow-lg whitespace-nowrap opacity-0 pointer-events-none group-hover:opacity-100 transition-opacity z-50 flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-[#D97757]" />
                <span>Bookings</span>
              </div>
            )}
          </NavLink>

          {/* Analytics */}
          {(isManagerOrAdmin || !isExpanded) && (
            <NavLink
              to="/analytics"
              className={({ isActive }) =>
                isExpanded ? navItemClass(isActive) : navItemCollapsedClass(isActive)
              }
            >
              <span className="material-symbols-outlined text-[20px] shrink-0 transition-colors">
                bar_chart
              </span>
              {isExpanded ? (
                <span className="truncate">Analytics</span>
              ) : (
                <div className="absolute left-full ml-3 px-2.5 py-1 bg-[#FAFAF8] border border-[#E5E0D8] text-[#242424] text-[11px] font-semibold rounded-[4px] shadow-lg whitespace-nowrap opacity-0 pointer-events-none group-hover:opacity-100 transition-opacity z-50 flex items-center gap-1.5">
                  <span className="w-1.5 h-1.5 rounded-full bg-[#D97757]" />
                  <span>Analytics</span>
                </div>
              )}
            </NavLink>
          )}

          {/* Admin Settings */}
          {isAdmin && (
            <NavLink
              to="/administration"
              className={({ isActive }) =>
                isExpanded ? navItemClass(isActive) : navItemCollapsedClass(isActive)
              }
            >
              <span className="material-symbols-outlined text-[20px] shrink-0 transition-colors">
                settings
              </span>
              {isExpanded ? (
                <span className="truncate">Admin Settings</span>
              ) : (
                <div className="absolute left-full ml-3 px-2.5 py-1 bg-[#FAFAF8] border border-[#E5E0D8] text-[#242424] text-[11px] font-semibold rounded-[4px] shadow-lg whitespace-nowrap opacity-0 pointer-events-none group-hover:opacity-100 transition-opacity z-50 flex items-center gap-1.5">
                  <span className="w-1.5 h-1.5 rounded-full bg-[#D97757]" />
                  <span>Admin Settings</span>
                </div>
              )}
            </NavLink>
          )}

          {/* Notifications */}
          <div className="relative">
            <button
              type="button"
              onClick={() => setShowNotifications(!showNotifications)}
              className={`w-full flex items-center ${
                isExpanded ? 'justify-between ' + navItemClass(showNotifications) : navItemCollapsedClass(showNotifications)
              }`}
            >
              <div className="flex items-center gap-3">
                <span className="material-symbols-outlined text-[20px] shrink-0">
                  notifications
                </span>
                {isExpanded && <span className="truncate">Notifications</span>}
              </div>
              {unreadCount > 0 && isExpanded && (
                <span
                  className={`px-1.5 py-0.5 rounded-full text-[10px] font-bold ${
                    showNotifications ? 'bg-white text-[#D97757]' : 'bg-[#D97757] text-white'
                  }`}
                >
                  {unreadCount}
                </span>
              )}
              {!isExpanded && (
                <div className="absolute left-full ml-3 px-2.5 py-1 bg-[#FAFAF8] border border-[#E5E0D8] text-[#242424] text-[11px] font-semibold rounded-[4px] shadow-lg whitespace-nowrap opacity-0 pointer-events-none group-hover:opacity-100 transition-opacity z-50 flex items-center gap-1.5">
                  <span className="w-1.5 h-1.5 rounded-full bg-[#D97757]" />
                  <span>Notifications ({unreadCount})</span>
                </div>
              )}
            </button>

            {/* Notifications Popover */}
            {showNotifications && (
              <div className="absolute left-full ml-3 bottom-0 w-72 bg-[#FAFAF8] border border-[#E5E0D8] rounded-[8px] shadow-xl p-3 z-50 animate-in fade-in zoom-in-95">
                <div className="flex items-center justify-between pb-2 border-b border-[#E5E0D8] mb-2">
                  <span className="text-[10px] uppercase font-[500] tracking-[0.08em] text-[#A8A29E]">
                    Notifications
                  </span>
                  <button
                    onClick={markAllAsRead}
                    className="text-[11px] text-[#D97757] hover:underline font-semibold cursor-pointer"
                  >
                    Mark read
                  </button>
                </div>
                <div className="space-y-1.5 max-h-56 overflow-y-auto">
                  {notifications.map((n) => (
                    <div
                      key={n.id}
                      className={`p-2 rounded-[4px] text-xs transition-colors ${
                        !n.isRead ? 'bg-[#F3F2EF] text-[#242424]' : 'text-[#6B6560]'
                      }`}
                    >
                      <p className="font-semibold text-xs leading-tight text-[#242424]">{n.title}</p>
                      <p className="text-[11px] text-[#6B6560] mt-0.5 leading-snug">{n.body}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </nav>
      </div>

      {/* ========================================================= */}
      {/* BOTTOM (PINNED): User Profile Card + Collapse Toggle      */}
      {/* ========================================================= */}
      <div className="flex flex-col px-2 space-y-2 relative">
        {/* Bottom user profile card: same light background (#F3F2EF), subtle border-top */}
        <div className="pt-2 border-t border-[#E5E3DF] relative">
          {isExpanded ? (
            <div className="relative">
              <button
                type="button"
                onClick={() => {
                  setShowProfileMenu(!showProfileMenu);
                  setShowNotifications(false);
                }}
                className="w-full flex items-center justify-between px-2.5 py-2 text-xs hover:bg-[#EAE8E4] transition-colors rounded-lg cursor-pointer group"
              >
                <div className="flex items-center gap-2.5 min-w-0">
                  <img
                    alt="Profile"
                    className="w-8 h-8 rounded-full object-cover shrink-0 ring-1 ring-[#D5D0C8]"
                    src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=120&q=80"
                  />
                  <div className="flex flex-col text-left truncate">
                    <span className="text-xs font-semibold text-[#242424] leading-tight truncate">
                      {user?.fullName || 'Morgan Vance'}
                    </span>
                    <span className="text-[10px] text-[#6B6560] font-mono leading-tight">
                      {role}
                    </span>
                  </div>
                </div>
                <span className="material-symbols-outlined text-[16px] text-[#6B6560] group-hover:text-[#242424] transition-colors shrink-0">
                  chevron_right
                </span>
              </button>

              {/* Upward Profile Menu Dropdown */}
              {showProfileMenu && (
                <div className="absolute bottom-full left-0 mb-2 w-56 bg-[#FAFAF8] border border-[#E5E0D8] rounded-[8px] shadow-xl p-2.5 z-50">
                  <div className="pb-2 border-b border-[#E5E0D8] mb-2">
                    <p className="text-xs font-bold text-[#242424]">{user?.fullName}</p>
                    <p className="text-[11px] text-[#6B6560] truncate">{user?.email}</p>
                  </div>

                  <div className="space-y-1">
                    <span className="text-[10px] uppercase tracking-[0.08em] font-[500] text-[#A8A29E] px-2 block mb-1">
                      Switch Role
                    </span>
                    {(['EMPLOYEE', 'MANAGER', 'ADMIN'] as Role[]).map((r) => (
                      <button
                        key={r}
                        onClick={() => {
                          switchRole(r);
                          setShowProfileMenu(false);
                        }}
                        className={`w-full text-left px-2.5 py-1.5 rounded-[4px] text-xs transition-colors cursor-pointer ${
                          role === r
                            ? 'bg-[#D97757] text-white font-semibold'
                            : 'text-[#6B6560] hover:text-[#242424] hover:bg-[#F3F2EF]'
                        }`}
                      >
                        {r}
                      </button>
                    ))}
                  </div>

                  <div className="pt-2 border-t border-[#E5E0D8] mt-2">
                    <button
                      onClick={logout}
                      className="w-full text-left px-2.5 py-1.5 rounded-[4px] text-xs font-medium text-[#D97757] hover:bg-[#F3F2EF] transition-colors cursor-pointer"
                    >
                      Sign Out
                    </button>
                  </div>
                </div>
              )}
            </div>
          ) : (
            /* Collapsed Avatar 32px */
            <div className="flex justify-center relative group">
              <div
                onClick={() => setShowProfileMenu(!showProfileMenu)}
                className="cursor-pointer hover:opacity-90 transition-opacity"
                title={`${user?.fullName} (${role})`}
              >
                <img
                  alt="Profile"
                  className="w-8 h-8 rounded-full object-cover ring-1 ring-[#D5D0C8] hover:ring-[#D97757] transition-all"
                  src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=120&q=80"
                />
              </div>

              <div className="absolute left-full ml-3 bottom-0 px-2.5 py-1 bg-[#FAFAF8] border border-[#E5E0D8] text-[#242424] text-[11px] font-semibold rounded-[4px] shadow-lg whitespace-nowrap opacity-0 pointer-events-none group-hover:opacity-100 transition-opacity z-50">
                {user?.fullName || 'Morgan Vance'} ({role})
              </div>
            </div>
          )}
        </div>

        {/* ========================================================= */}
        {/* Refined Collapse Toggle at bottom                          */}
        {/* ========================================================= */}
        <div className="pt-1 border-t border-[#E5E3DF]">
          {isExpanded ? (
            <button
              onClick={toggleCollapse}
              title="Collapse sidebar"
              className="w-full flex items-center justify-between px-3 py-2 text-xs rounded-lg text-[#6B6560] hover:text-[#242424] hover:bg-[#EAE8E4] transition-all cursor-pointer group"
            >
              <div className="flex items-center gap-2.5">
                <span className="material-symbols-outlined text-[18px] group-hover:-translate-x-0.5 transition-transform">
                  keyboard_double_arrow_left
                </span>
                <span className="font-medium text-[11px]">Collapse</span>
              </div>
              <span className="text-[10px] font-mono text-[#6B6560] bg-[#EAE8E4] px-1.5 py-0.5 rounded-[3px] border border-[#D5D0C8]">
                ⌘B
              </span>
            </button>
          ) : (
            <div className="relative group flex justify-center">
              <button
                onClick={toggleCollapse}
                title="Expand sidebar"
                className="w-9 h-9 rounded-lg flex items-center justify-center text-[#6B6560] hover:text-[#242424] hover:bg-[#EAE8E4] transition-all cursor-pointer"
              >
                <span className="material-symbols-outlined text-[18px] group-hover:translate-x-0.5 transition-transform">
                  keyboard_double_arrow_right
                </span>
              </button>

              <div className="absolute left-full ml-3 bottom-1 px-2.5 py-1 bg-[#FAFAF8] border border-[#E5E0D8] text-[#242424] text-[11px] font-semibold rounded-[4px] shadow-lg whitespace-nowrap opacity-0 pointer-events-none group-hover:opacity-100 transition-opacity z-50">
                Expand Sidebar
              </div>
            </div>
          )}
        </div>
      </div>
    </aside>
  );
};
