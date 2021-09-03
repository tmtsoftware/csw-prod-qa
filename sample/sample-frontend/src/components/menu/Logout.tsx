import { Menu } from 'antd'
import React from 'react'

export const Logout = ({ logout }: { logout: () => void }): JSX.Element => (
  <Menu.Item key='logout' onClick={logout}>
    Logout
  </Menu.Item>
)
