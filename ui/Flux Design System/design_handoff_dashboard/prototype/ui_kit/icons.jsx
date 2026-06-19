/* Stroke icon set — lifted from ui/src/shared/ui/icons.tsx.
   Lucide-style: 24×24, stroke 1.8, round caps/joins, sized 16px by default. */
const { createElement: h } = React;

function Svg({ size = 16, children, style, ...rest }) {
  return h('svg', {
    viewBox: '0 0 24 24', width: size, height: size,
    fill: 'none', stroke: 'currentColor', strokeWidth: 1.8,
    strokeLinecap: 'round', strokeLinejoin: 'round',
    'aria-hidden': true, style, ...rest,
  }, children);
}
const P = (d) => h('path', { d, key: d });

const FilterIcon  = (p) => h(Svg, p, [P('M3 5h18'), P('M6 12h12'), P('M10 19h4')]);
const ApplyIcon   = (p) => h(Svg, p, [P('M20 6 9 17l-5-5')]);
const ResetIcon   = (p) => h(Svg, p, [P('M3 12a9 9 0 1 0 2.64-6.36'), P('M3 4v5h5')]);
const ReplayIcon  = ResetIcon;
const NextIcon    = (p) => h(Svg, p, [P('m9 18 6-6-6-6')]);
const PrevIcon    = (p) => h(Svg, p, [P('m15 18-6-6 6-6')]);
const PauseIcon   = (p) => h(Svg, p, [P('M9 5v14'), P('M15 5v14')]);
const ResumeIcon  = (p) => h(Svg, p, [P('m8 5 11 7-11 7z')]);
const CloseIcon   = (p) => h(Svg, p, [P('M18 6 6 18'), P('m6 6 12 12')]);
const RefreshIcon = ResetIcon;
const LogoutIcon  = (p) => h(Svg, p, [P('M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4'), P('m16 17 5-5-5-5'), P('M21 12H9')]);
const SearchIcon  = (p) => h(Svg, p, [P('M11 11m-7 0a7 7 0 1 0 14 0a7 7 0 1 0-14 0'), P('m21 21-4.3-4.3')]);
const SunIcon     = (p) => h(Svg, p, [h('circle',{cx:12,cy:12,r:4,key:'c'}), P('M12 2v2'), P('M12 20v2'), P('m4.9 4.9 1.4 1.4'), P('m17.7 17.7 1.4 1.4'), P('M2 12h2'), P('M20 12h2'), P('m4.9 19.1 1.4-1.4'), P('m17.7 6.3 1.4-1.4')]);
const MoonIcon    = (p) => h(Svg, p, [P('M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z')]);

Object.assign(window, {
  FilterIcon, ApplyIcon, ResetIcon, ReplayIcon, NextIcon, PrevIcon,
  PauseIcon, ResumeIcon, CloseIcon, RefreshIcon, LogoutIcon, SearchIcon, SunIcon, MoonIcon,
});
