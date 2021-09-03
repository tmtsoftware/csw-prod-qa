import { Menu } from 'antd'
import React from 'react'

export const Login = ({ login }: { login: () => void }): JSX.Element => (
  <Menu.Item key='login' onClick={login}>
    Login
  </Menu.Item>
)
