import { ImageResponse } from 'next/og';

export const size = { width: 1200, height: 630 };
export const contentType = 'image/png';
export const alt = 'HokieHub — the Virginia Tech student marketplace';

/** Generated at build time so pasting the link anywhere yields a real card. */
export default function OgImage() {
  return new ImageResponse(
    (
      <div
        style={{
          width: '100%',
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          background: '#630031',
          padding: 80,
        }}
      >
        <div style={{ fontSize: 28, color: '#CF4420', letterSpacing: 4 }}>HOKIEHUB</div>
        <div style={{ fontSize: 66, color: '#F7F3EE', marginTop: 26, lineHeight: 1.15 }}>
          Buy and sell within Virginia Tech.
        </div>
        <div style={{ fontSize: 28, color: '#F7F3EE', opacity: 0.75, marginTop: 30, lineHeight: 1.4 }}>
          Textbooks, dorm furniture and tutoring — verified @vt.edu accounts only
        </div>
      </div>
    ),
    size,
  );
}
