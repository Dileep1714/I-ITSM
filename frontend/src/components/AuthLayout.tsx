import { Anchor } from '@mantine/core';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import classes from './AuthLayout.module.css';
import { BrandMark } from './BrandMark';

const HANDLES: [string, string][] = [
  ['Prioritised automatically', 'P1–P4'],
  ['Resolution deadlines tracked and forecast', 'SLA'],
  ['Updates the moment something changes', 'LIVE'],
];

/**
 * Signed-out frame: the front of the IT service desk. One ruled sheet with three columns: what the desk does,
 * the form (sign-in or registration), and the help people need when they arrive.
 */
export function AuthLayout({
  title,
  subtitle,
  formRef,
  variant = 'signin',
  children,
}: {
  title: string;
  subtitle?: string;
  /** Typed form reference shown in the header band. */
  formRef?: string;
  variant?: 'signin' | 'register';
  children: ReactNode;
}) {
  return (
    <div className={classes.page}>
      <div className={classes.sheet}>
        <header className={classes.head}>
          <BrandMark />
          <span className={classes.ref}>IT SERVICE DESK · {formRef ?? (variant === 'signin' ? 'FORM IT-01 · SIGN-IN' : 'FORM IT-02 · NEW ACCOUNT')}</span>
        </header>

        <div className={classes.cols}>
          <section className={`${classes.col} ${classes.intro}`}>
            <h1 className={classes.display}>The IT service desk, open for your problem.</h1>
            <p className={classes.lede}>
              Report what is broken, follow it until it is fixed, and see every step stamped along the way.
            </p>
            <ul className={classes.handles}>
              {HANDLES.map(([text, tag]) => (
                <li key={tag}>
                  <span>{text}</span>
                  <span className={classes.tag}>{tag}</span>
                </li>
              ))}
            </ul>
          </section>

          <main className={`${classes.col} ${classes.formCol}`}>
            <h2 className={classes.formTitle}>{title}</h2>
            {subtitle && <p className={classes.formHint}>{subtitle}</p>}
            {children}
          </main>

          <aside className={classes.col} aria-label="Help">
            <span className={classes.label}>Need help?</span>
            <ul className={classes.help}>
              <li>
                <b>Forgot your password?</b>
                <span>Your IT administrator can reset it for you.</span>
              </li>
              {variant === 'signin' ? (
                <li>
                  <b>New employee?</b>
                  <span>
                    <Anchor component={Link} to="/register" size="sm">
                      Create your account
                    </Anchor>{' '}
                    with your work email.
                  </span>
                </li>
              ) : (
                <li>
                  <b>Already have an account?</b>
                  <span>
                    <Anchor component={Link} to="/login" size="sm">
                      Sign in
                    </Anchor>{' '}
                    instead.
                  </span>
                </li>
              )}
              <li>
                <b>Something urgent?</b>
                <span>Sign in and report it: critical incidents reach a technician first.</span>
              </li>
            </ul>
          </aside>
        </div>

        <footer className={classes.foot}>
          <span>SERVICE MANAGEMENT · INCIDENT RESOLUTION</span>
          <span className={classes.footCourse}>CS F213 · 2026–27</span>
        </footer>
      </div>
    </div>
  );
}
