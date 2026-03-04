declare module "sockjs-client" {
  type SockJSOptions = Record<string, unknown>;

  interface SockJSConstructor {
    new (url: string, protocols?: string | string[] | null, options?: SockJSOptions): WebSocket;
  }

  const SockJS: SockJSConstructor;
  export default SockJS;
}
